package com.teamformation.backend.engine.formation.persistence;

import com.teamformation.backend.engine.constraint.ConstraintConfiguration;
import com.teamformation.backend.engine.constraint.MandatorySkill;
import com.teamformation.backend.engine.constraint.ProhibitedPair;
import com.teamformation.backend.engine.constraint.TeamSizePlanner;
import com.teamformation.backend.engine.formation.FormationInput;
import com.teamformation.backend.engine.formation.FormationPreference;
import com.teamformation.backend.engine.formation.FormationStudent;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.exception.FormationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence adapter for the immutable T1 input snapshot.
 *
 * <p>The snapshot is the authoritative input of a formation run. T2 and any
 * future Engine 4 consumer rebuild {@link FormationInput} from these rows only;
 * they never read live roster, preference or skill rows. The store is a JDBC
 * adapter, not a JPA entity, because a snapshot is append-only history and is
 * never mutated after T1 commits.</p>
 *
 * <p>A canonical SHA-256 hash of every input field is stored with the header and
 * is recomputed on load. Any divergence (manual tampering, a changed schema
 * interpretation, or a partial copy) is rejected as
 * {@code SNAPSHOT_INTEGRITY} before the engine runs.</p>
 */
@Component
public class FormationInputSnapshotStore {

    public static final int SNAPSHOT_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;

    public FormationInputSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Persists one snapshot inside the caller's transaction and returns the
     * canonical hash that was stored.
     */
    public String persist(FormationJob job, FormationInput input) {
        Objects.requireNonNull(job, "job is required");
        Objects.requireNonNull(job.getId(), "job must be persisted before its snapshot");
        Objects.requireNonNull(input, "input is required");

        String hash = canonicalHash(input);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO formation_input_snapshots (
                        formation_job_id, project_id, snapshot_version, input_hash,
                        min_team_size, max_team_size, team_size_slack,
                        constraint_version, seed, algorithm_version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, job.getId());
            statement.setLong(2, input.projectId());
            statement.setInt(3, SNAPSHOT_VERSION);
            statement.setString(4, hash);
            statement.setInt(5, input.minTeamSize());
            statement.setInt(6, input.maxTeamSize());
            statement.setInt(7, input.teamSizeSlack());
            statement.setInt(8, input.constraintVersion());
            statement.setLong(9, input.seed());
            statement.setString(10, input.algorithmVersion());
            statement.setTimestamp(11, Timestamp.from(Instant.now()));
            return statement;
        }, keyHolder);
        long snapshotId = Objects.requireNonNull(
                keyHolder.getKey(), "snapshot id must be generated").longValue();

        List<Object[]> studentRows = new ArrayList<>(input.students().size());
        List<Object[]> skillRows = new ArrayList<>();
        for (FormationStudent student : input.students()) {
            studentRows.add(new Object[]{snapshotId, student.studentId()});
            for (Map.Entry<Long, Integer> skill
                    : sortedEntries(student.proficiencyBySkillId())) {
                skillRows.add(new Object[]{
                        snapshotId, student.studentId(), skill.getKey(), skill.getValue()});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO formation_input_snapshot_students (snapshot_id, student_id) VALUES (?, ?)",
                studentRows);

        if (!skillRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO formation_input_snapshot_skills
                        (snapshot_id, student_id, skill_id, proficiency)
                    VALUES (?, ?, ?, ?)
                    """, skillRows);
        }

        List<Object[]> preferenceRows = new ArrayList<>(input.preferences().size());
        for (FormationPreference preference : sortedPreferences(input.preferences())) {
            preferenceRows.add(new Object[]{
                    snapshotId,
                    preference.studentId(),
                    preference.preferredStudentId(),
                    preference.rank()});
        }
        if (!preferenceRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO formation_input_snapshot_preferences
                        (snapshot_id, student_id, preferred_student_id, preference_rank)
                    VALUES (?, ?, ?, ?)
                    """, preferenceRows);
        }

        List<Object[]> constraintRows = new ArrayList<>();
        for (ProhibitedPair pair : sortedPairs(input.prohibitedPairs())) {
            constraintRows.add(new Object[]{
                    snapshotId,
                    "HARD",
                    "PROHIBITED_PAIR",
                    pair.studentId1() + "," + pair.studentId2(),
                    1.0,
                    input.constraintVersion()});
        }
        for (MandatorySkill skill : sortedMandatory(input.mandatorySkills())) {
            constraintRows.add(new Object[]{
                    snapshotId,
                    "HARD",
                    "MANDATORY_SKILL",
                    skill.skillId() + ":" + skill.minimumProficiency(),
                    1.0,
                    input.constraintVersion()});
        }
        if (!constraintRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO formation_input_snapshot_constraints
                        (snapshot_id, constraint_type, rule_type, rule_configuration,
                         weight, source_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, constraintRows);
        }

        List<Object[]> skillNameRows = new ArrayList<>(input.skillNames().size());
        for (Map.Entry<Long, String> entry : sortedEntries(input.skillNames())) {
            skillNameRows.add(new Object[]{snapshotId, entry.getKey(), entry.getValue()});
        }
        if (!skillNameRows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO formation_input_snapshot_skill_names
                        (snapshot_id, skill_id, skill_name)
                    VALUES (?, ?, ?)
                    """, skillNameRows);
        }

        return hash;
    }

    /**
     * Loads the snapshot for one job and verifies its integrity against the
     * job header. The job must already be loaded by the caller.
     */
    public FormationInput loadForJob(FormationJob job) {
        SnapshotHeader header = findHeader(job.getId())
                .orElseThrow(() -> FormationException.snapshotIntegrity(
                        "the formation job has no input snapshot and cannot be resumed"));

        if (!Objects.equals(header.projectId(), job.getProject().getId())) {
            throw FormationException.snapshotIntegrity(
                    "the snapshot project does not match the formation job");
        }
        if (header.constraintVersion() != job.getConstraintVersion()) {
            throw FormationException.snapshotIntegrity(
                    "the snapshot constraint version does not match the formation job");
        }
        if (job.getSeed() != null && header.seed() != job.getSeed()) {
            throw FormationException.snapshotIntegrity(
                    "the snapshot seed does not match the formation job");
        }
        if (!Objects.equals(header.algorithmVersion(), job.getAlgorithmVersion())) {
            throw FormationException.snapshotIntegrity(
                    "the snapshot algorithm version does not match the formation job");
        }

        FormationInput input = readInput(header);
        String recomputed = canonicalHash(input);
        if (!recomputed.equals(header.inputHash())) {
            throw FormationException.snapshotIntegrity(
                    "the stored input hash does not match the reconstructed input");
        }
        return input;
    }

    public Optional<SnapshotHeader> findHeader(Long jobId) {
        List<SnapshotHeader> rows = jdbcTemplate.query("""
                SELECT id, formation_job_id, project_id, snapshot_version, input_hash,
                       min_team_size, max_team_size, team_size_slack,
                       constraint_version, seed, algorithm_version, created_at
                FROM formation_input_snapshots
                WHERE formation_job_id = ?
                """, (resultSet, rowNumber) -> new SnapshotHeader(
                resultSet.getLong("id"),
                resultSet.getLong("formation_job_id"),
                resultSet.getLong("project_id"),
                resultSet.getInt("snapshot_version"),
                resultSet.getString("input_hash"),
                resultSet.getInt("min_team_size"),
                resultSet.getInt("max_team_size"),
                resultSet.getInt("team_size_slack"),
                resultSet.getInt("constraint_version"),
                resultSet.getLong("seed"),
                resultSet.getString("algorithm_version"),
                resultSet.getTimestamp("created_at").toInstant()),
                jobId);
        return rows.stream().findFirst();
    }

    private FormationInput readInput(SnapshotHeader header) {
        long snapshotId = header.id();

        List<Long> studentIds = jdbcTemplate.queryForList("""
                SELECT student_id FROM formation_input_snapshot_students
                WHERE snapshot_id = ? ORDER BY student_id
                """, Long.class, snapshotId);

        Map<Long, Map<Long, Integer>> proficiencies = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT student_id, skill_id, proficiency
                FROM formation_input_snapshot_skills
                WHERE snapshot_id = ?
                ORDER BY student_id, skill_id
                """, resultSet -> {
            long studentId = resultSet.getLong("student_id");
            long skillId = resultSet.getLong("skill_id");
            int proficiency = resultSet.getInt("proficiency");
            proficiencies.computeIfAbsent(studentId, ignored -> new LinkedHashMap<>())
                    .put(skillId, proficiency);
        }, snapshotId);

        List<FormationStudent> students = new ArrayList<>(studentIds.size());
        for (Long studentId : studentIds) {
            students.add(new FormationStudent(
                    studentId, proficiencies.getOrDefault(studentId, Map.of())));
        }

        List<FormationPreference> preferences = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT student_id, preferred_student_id, preference_rank
                FROM formation_input_snapshot_preferences
                WHERE snapshot_id = ?
                ORDER BY student_id, preference_rank, preferred_student_id
                """, resultSet -> {
            preferences.add(new FormationPreference(
                    resultSet.getLong("student_id"),
                    resultSet.getLong("preferred_student_id"),
                    resultSet.getInt("preference_rank")));
        }, snapshotId);

        List<ProhibitedPair> prohibitedPairs = new ArrayList<>();
        List<MandatorySkill> mandatorySkills = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT rule_type, rule_configuration
                FROM formation_input_snapshot_constraints
                WHERE snapshot_id = ?
                ORDER BY id
                """, resultSet -> {
            String ruleType = resultSet.getString("rule_type");
            String configuration = resultSet.getString("rule_configuration");
            String[] parts = configuration.split("[:,]", -1);
            if ("PROHIBITED_PAIR".equals(ruleType)) {
                prohibitedPairs.add(new ProhibitedPair(
                        Long.parseLong(parts[0].trim()), Long.parseLong(parts[1].trim())));
            } else if ("MANDATORY_SKILL".equals(ruleType)) {
                mandatorySkills.add(new MandatorySkill(
                        Long.parseLong(parts[0].trim()), Integer.parseInt(parts[1].trim())));
            } else {
                throw FormationException.snapshotIntegrity(
                        "the snapshot contains an unsupported constraint rule type");
            }
        }, snapshotId);

        Map<Long, String> skillNames = new HashMap<>();
        jdbcTemplate.query("""
                SELECT skill_id, skill_name
                FROM formation_input_snapshot_skill_names
                WHERE snapshot_id = ?
                ORDER BY skill_id
                """, resultSet -> {
            skillNames.put(resultSet.getLong("skill_id"), resultSet.getString("skill_name"));
        }, snapshotId);

        return new FormationInput(
                header.projectId(),
                TeamSizePlanner.plan(
                        students.size(),
                        header.minTeamSize(),
                        header.maxTeamSize(),
                        header.teamSizeSlack()),
                header.minTeamSize(),
                header.maxTeamSize(),
                header.teamSizeSlack(),
                header.constraintVersion(),
                header.seed(),
                header.algorithmVersion(),
                students,
                preferences,
                prohibitedPairs,
                mandatorySkills,
                skillNames);
    }

    /**
     * Canonical, deterministic SHA-256 of every input field. The encoding is
     * versioned so a future snapshot format must be introduced explicitly.
     */
    public static String canonicalHash(FormationInput input) {
        StringBuilder canonical = new StringBuilder(4096);
        canonical.append("snapshot-version=").append(SNAPSHOT_VERSION).append('\n');
        canonical.append("project-id=").append(input.projectId()).append('\n');
        canonical.append("min-team-size=").append(input.minTeamSize()).append('\n');
        canonical.append("max-team-size=").append(input.maxTeamSize()).append('\n');
        canonical.append("team-size-slack=").append(input.teamSizeSlack()).append('\n');
        canonical.append("constraint-version=").append(input.constraintVersion()).append('\n');
        canonical.append("seed=").append(input.seed()).append('\n');
        canonical.append("algorithm-version=").append(input.algorithmVersion()).append('\n');
        canonical.append("planner=").append(joinInts(input.teamSizes())).append('\n');

        List<FormationStudent> students = new ArrayList<>(input.students());
        students.sort(Comparator.comparingLong(FormationStudent::studentId));
        canonical.append("students=");
        for (int index = 0; index < students.size(); index++) {
            if (index > 0) {
                canonical.append(',');
            }
            canonical.append(students.get(index).studentId());
        }
        canonical.append('\n');

        canonical.append("skills=");
        boolean first = true;
        for (FormationStudent student : students) {
            for (Map.Entry<Long, Integer> entry
                    : sortedEntries(student.proficiencyBySkillId())) {
                if (!first) {
                    canonical.append(';');
                }
                first = false;
                canonical.append(student.studentId()).append(':')
                        .append(entry.getKey()).append(':').append(entry.getValue());
            }
        }
        canonical.append('\n');

        canonical.append("preferences=");
        first = true;
        for (FormationPreference preference : sortedPreferences(input.preferences())) {
            if (!first) {
                canonical.append(';');
            }
            first = false;
            canonical.append(preference.studentId()).append(':')
                    .append(preference.preferredStudentId()).append(':')
                    .append(preference.rank());
        }
        canonical.append('\n');

        canonical.append("prohibited-pairs=");
        first = true;
        for (ProhibitedPair pair : sortedPairs(input.prohibitedPairs())) {
            if (!first) {
                canonical.append(';');
            }
            first = false;
            canonical.append(pair.studentId1()).append(':').append(pair.studentId2());
        }
        canonical.append('\n');

        canonical.append("mandatory-skills=");
        first = true;
        for (MandatorySkill skill : sortedMandatory(input.mandatorySkills())) {
            if (!first) {
                canonical.append(';');
            }
            first = false;
            canonical.append(skill.skillId()).append(':')
                    .append(skill.minimumProficiency());
        }
        canonical.append('\n');

        canonical.append("skill-names=");
        first = true;
        for (Map.Entry<Long, String> entry : sortedEntries(input.skillNames())) {
            if (!first) {
                canonical.append(';');
            }
            first = false;
            canonical.append(entry.getKey()).append(':').append(entry.getValue());
        }
        canonical.append('\n');

        return sha256(canonical.toString());
    }

    private static String joinInts(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private static List<FormationPreference> sortedPreferences(
            List<FormationPreference> preferences) {
        List<FormationPreference> sorted = new ArrayList<>(preferences);
        sorted.sort(Comparator
                .comparingLong(FormationPreference::studentId)
                .thenComparingInt(FormationPreference::rank)
                .thenComparingLong(FormationPreference::preferredStudentId));
        return sorted;
    }

    private static List<ProhibitedPair> sortedPairs(List<ProhibitedPair> pairs) {
        List<ProhibitedPair> sorted = new ArrayList<>(pairs);
        sorted.sort(Comparator
                .comparingLong(ProhibitedPair::studentId1)
                .thenComparingLong(ProhibitedPair::studentId2));
        return sorted;
    }

    private static List<MandatorySkill> sortedMandatory(List<MandatorySkill> skills) {
        List<MandatorySkill> sorted = new ArrayList<>(skills);
        sorted.sort(Comparator
                .comparingLong(MandatorySkill::skillId)
                .thenComparingInt(MandatorySkill::minimumProficiency));
        return sorted;
    }

    private static <K extends Comparable<K>, V> List<Map.Entry<K, V>> sortedEntries(
            Map<K, V> values) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Immutable header row used by loaders and tests.
     */
    public record SnapshotHeader(
            long id,
            long jobId,
            long projectId,
            int snapshotVersion,
            String inputHash,
            int minTeamSize,
            int maxTeamSize,
            int teamSizeSlack,
            int constraintVersion,
            long seed,
            String algorithmVersion,
            Instant createdAt
    ) {
    }
}
