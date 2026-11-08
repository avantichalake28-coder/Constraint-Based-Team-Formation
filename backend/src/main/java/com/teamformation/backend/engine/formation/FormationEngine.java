package com.teamformation.backend.engine.formation;

import com.teamformation.backend.engine.constraint.MandatorySkill;
import com.teamformation.backend.engine.constraint.ProhibitedPair;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, stateless Engine 3 formation engine.
 *
 * <p>The algorithm is a bounded heuristic: deterministic greedy construction
 * with backtracking repair, followed by a bounded best-improvement pairwise-swap
 * local search. It never claims global optimality. All ordering decisions are
 * stable and every pseudo-random value is derived from the recorded seed.</p>
 *
 * <p>A <b>search node</b> is one attempt to place one ordered student into one
 * candidate team after capacity and empty-team symmetry filtering. The node
 * budget bounds these attempts.</p>
 */
@Component
public class FormationEngine {

    public FormationPlan form(FormationInput input, FormationSearchLimits limits) {
        Objects.requireNonNull(input, "input is required");
        Objects.requireNonNull(limits, "limits are required");

        if (!input.planner().feasible()) {
            throw new NoValidFormationException(
                    "The Engine 2 team-size plan is infeasible: "
                            + Objects.requireNonNullElse(input.planner().explanation(), "no explanation"));
        }

        List<Integer> teamSizes = input.teamSizes();
        if (teamSizes.isEmpty() || input.students().isEmpty()) {
            throw new NoValidFormationException(
                    "A formation needs at least one student and one planned team");
        }

        int capacity = teamSizes.stream().mapToInt(Integer::intValue).sum();
        if (capacity != input.students().size()) {
            throw new IllegalArgumentException(
                    "The team-size plan capacity " + capacity
                            + " does not match the eligible student count " + input.students().size());
        }

        if (limits.isExpired(Instant.now())) {
            throw new FormationExecutionBudgetExceededException();
        }
        SearchState state = new SearchState(input, limits);
        if (!state.search(0)) {
            throw new NoValidFormationException(
                    "No complete arrangement satisfies the hard constraints");
        }
        state.runLocalSearch();
        return state.toPlan();
    }

    /**
     * Mutable search state confined to one synchronous invocation.
     */
    private static final class SearchState {

        private final FormationInput input;
        private final FormationSearchLimits limits;
        private final List<Integer> teamSizes;
        private final List<FormationStudent> orderedStudents;
        private final Map<Long, Integer> indexByStudentId = new HashMap<>();
        private final Map<Long, FormationStudent> studentById = new HashMap<>();
        private final Map<Long, List<FormationPreference>> preferencesByStudent;
        private final Map<Long, List<Long>> preferredBy;
        private final Map<Long, Map<Long, Double>> outgoingWeights = new HashMap<>();
        private final Map<Long, Set<Long>> prohibitedByStudent = new HashMap<>();
        private final List<MandatorySkill> mandatorySkills;
        private final Map<Long, Set<Long>> qualifyingSkillsByStudent = new HashMap<>();
        private final boolean[][] qualifies;
        private final int[][] suffixQualified;

        private final int teamCount;
        private final List<List<Long>> teamMembers = new ArrayList<>();
        private final Map<Long, Integer> teamOf = new HashMap<>();
        private final int[][] coverage;
        private int nodes = 0;

        SearchState(FormationInput input, FormationSearchLimits limits) {
            this.input = input;
            this.limits = limits;
            this.teamSizes = input.teamSizes();
            this.teamCount = teamSizes.size();
            this.mandatorySkills = input.mandatorySkills();
            this.preferencesByStudent = CompatibilityScorer.indexByStudent(input.preferences());
            this.preferredBy = CompatibilityScorer.indexPreferredBy(input.preferences());

            for (FormationPreference preference : input.preferences()) {
                outgoingWeights
                        .computeIfAbsent(preference.studentId(), ignored -> new HashMap<>())
                        .put(preference.preferredStudentId(), preference.weight());
            }

            for (ProhibitedPair pair : input.prohibitedPairs()) {
                addProhibited(pair.studentId1(), pair.studentId2());
                addProhibited(pair.studentId2(), pair.studentId1());
            }

            for (int i = 0; i < teamCount; i++) {
                teamMembers.add(new ArrayList<>(teamSizes.get(i)));
            }

            List<FormationStudent> sorted = new ArrayList<>(input.students());
            for (FormationStudent student : sorted) {
                studentById.put(student.studentId(), student);
            }
            for (MandatorySkill skill : mandatorySkills) {
                for (FormationStudent student : sorted) {
                    if (student.meets(skill.skillId(), skill.minimumProficiency())) {
                        qualifyingSkillsByStudent
                                .computeIfAbsent(student.studentId(), ignored -> new HashSet<>())
                                .add(skill.skillId());
                    }
                }
            }
            sorted.sort(studentComparator());
            this.orderedStudents = List.copyOf(sorted);
            for (int i = 0; i < orderedStudents.size(); i++) {
                indexByStudentId.put(orderedStudents.get(i).studentId(), i);
            }

            this.qualifies = new boolean[orderedStudents.size()][mandatorySkills.size()];
            for (int i = 0; i < orderedStudents.size(); i++) {
                for (int k = 0; k < mandatorySkills.size(); k++) {
                    qualifies[i][k] = orderedStudents.get(i)
                            .meets(mandatorySkills.get(k).skillId(),
                                    mandatorySkills.get(k).minimumProficiency());
                }
            }

            this.suffixQualified = new int[orderedStudents.size() + 1][mandatorySkills.size()];
            for (int i = orderedStudents.size() - 1; i >= 0; i--) {
                for (int k = 0; k < mandatorySkills.size(); k++) {
                    suffixQualified[i][k] = suffixQualified[i + 1][k] + (qualifies[i][k] ? 1 : 0);
                }
            }

            this.coverage = new int[teamCount][mandatorySkills.size()];
        }

        private void addProhibited(long studentId, long prohibitedId) {
            prohibitedByStudent
                    .computeIfAbsent(studentId, ignored -> new HashSet<>())
                    .add(prohibitedId);
        }

        private Comparator<FormationStudent> studentComparator() {
            return Comparator
                    .comparingInt((FormationStudent student) -> -mandatoryCoveredCount(student))
                    .thenComparingInt(student -> -prohibitedDegree(student.studentId()))
                    .thenComparingLong(student -> DeterministicTieBreaker
                            .studentRank(input.seed(), student.studentId()))
                    .thenComparingLong(FormationStudent::studentId);
        }

        private int mandatoryCoveredCount(FormationStudent student) {
            int covered = 0;
            for (MandatorySkill skill : mandatorySkills) {
                if (student.meets(skill.skillId(), skill.minimumProficiency())) {
                    covered++;
                }
            }
            return covered;
        }

        private int prohibitedDegree(long studentId) {
            return prohibitedByStudent.getOrDefault(studentId, Set.of()).size();
        }

        // ------------------------------------------------------------------
        // Backtracking construction
        // ------------------------------------------------------------------

        private boolean search(int index) {
            if (limits.isExpired(Instant.now())) {
                throw new FormationExecutionBudgetExceededException();
            }
            if (index == orderedStudents.size()) {
                return true;
            }

            FormationStudent student = orderedStudents.get(index);
            List<TeamChoice> choices = candidateTeams(index, student);

            for (TeamChoice choice : choices) {
                nodes++;
                if (nodes > limits.maxNodes()) {
                    throw new FormationSearchLimitReachedException(limits.maxNodes());
                }
                if (!canPlace(index, student, choice.teamIndex())) {
                    continue;
                }
                place(index, student.studentId(), choice.teamIndex());
                if (search(index + 1)) {
                    return true;
                }
                unplace(index, student.studentId(), choice.teamIndex());
            }
            return false;
        }

        private List<TeamChoice> candidateTeams(int index, FormationStudent student) {
            List<TeamChoice> choices = new ArrayList<>(teamCount);
            for (int team = 0; team < teamCount; team++) {
                if (teamMembers.get(team).size() >= teamSizes.get(team)) {
                    continue;
                }
                if (teamMembers.get(team).isEmpty() && hasEarlierEmptyTeamOfSameSize(team)) {
                    // Symmetry breaking: empty teams of identical size are
                    // interchangeable; only the canonical first one is explored.
                    continue;
                }

                int coveredUnmet = 0;
                for (int k = 0; k < mandatorySkills.size(); k++) {
                    if (qualifies[index][k] && coverage[team][k] == 0) {
                        coveredUnmet++;
                    }
                }
                double incremental = incrementalPreference(student.studentId(), team);
                long teamRank = DeterministicTieBreaker.teamRank(input.seed(), team);
                choices.add(new TeamChoice(team, coveredUnmet, incremental, teamRank));
            }

            choices.sort(Comparator
                    .comparingInt(TeamChoice::coveredUnmet).reversed()
                    .thenComparing(Comparator.comparingDouble(TeamChoice::incremental).reversed())
                    .thenComparingLong(TeamChoice::teamRank)
                    .thenComparingInt(TeamChoice::teamIndex));
            return choices;
        }

        private boolean hasEarlierEmptyTeamOfSameSize(int team) {
            for (int earlier = 0; earlier < team; earlier++) {
                if (teamMembers.get(earlier).isEmpty()
                        && teamSizes.get(earlier).equals(teamSizes.get(team))) {
                    return true;
                }
            }
            return false;
        }

        private double incrementalPreference(long studentId, int team) {
            double score = 0.0;
            Map<Long, Double> outgoing = outgoingWeights.getOrDefault(studentId, Map.of());
            for (Long member : teamMembers.get(team)) {
                score += outgoing.getOrDefault(member, 0.0);
                score += outgoingWeights
                        .getOrDefault(member, Map.of())
                        .getOrDefault(studentId, 0.0);
            }
            return score;
        }

        private boolean canPlace(int index, FormationStudent student, int team) {
            Set<Long> forbidden = prohibitedByStudent.getOrDefault(student.studentId(), Set.of());
            for (Long member : teamMembers.get(team)) {
                if (forbidden.contains(member)) {
                    return false;
                }
            }

            for (int k = 0; k < mandatorySkills.size(); k++) {
                int teamCoveredAfter = coverage[team][k] + (qualifies[index][k] ? 1 : 0);
                int teamsNeedingCoverage = 0;
                for (int other = 0; other < teamCount; other++) {
                    int effective = other == team ? teamCoveredAfter : coverage[other][k];
                    if (effective == 0) {
                        teamsNeedingCoverage++;
                    }
                }
                if (teamsNeedingCoverage > suffixQualified[index + 1][k]) {
                    return false;
                }
            }
            return true;
        }

        private void place(int index, long studentId, int team) {
            teamMembers.get(team).add(studentId);
            teamOf.put(studentId, team);
            for (int k = 0; k < mandatorySkills.size(); k++) {
                if (qualifies[index][k]) {
                    coverage[team][k]++;
                }
            }
        }

        private void unplace(int index, long studentId, int team) {
            List<Long> members = teamMembers.get(team);
            if (!members.isEmpty() && members.get(members.size() - 1) == studentId) {
                members.remove(members.size() - 1);
            } else {
                members.remove(studentId);
            }
            teamOf.remove(studentId);
            for (int k = 0; k < mandatorySkills.size(); k++) {
                if (qualifies[index][k]) {
                    coverage[team][k]--;
                }
            }
        }

        // ------------------------------------------------------------------
        // Bounded pairwise-swap local search
        // ------------------------------------------------------------------

        private void runLocalSearch() {
            for (List<Long> members : teamMembers) {
                members.sort(Long::compareTo);
            }

            int passes = 0;
            FormationObjective current = objective();
            while (passes < limits.maxImprovementPasses()) {
                if (limits.isExpired(Instant.now())) {
                    throw new FormationExecutionBudgetExceededException();
                }
                Swap best = null;
                FormationObjective bestObjective = null;

                for (int firstTeam = 0; firstTeam < teamCount; firstTeam++) {
                    List<Long> firstMembers = teamMembers.get(firstTeam);
                    for (int secondTeam = firstTeam + 1; secondTeam < teamCount; secondTeam++) {
                        List<Long> secondMembers = teamMembers.get(secondTeam);
                        for (Long firstStudent : firstMembers) {
                            for (Long secondStudent : secondMembers) {
                                if (!canSwap(firstStudent, firstTeam, secondStudent, secondTeam)) {
                                    continue;
                                }
                                FormationObjective candidate = evaluateSwap(
                                        firstStudent, firstTeam, secondStudent, secondTeam, current);
                                if (candidate.compareTo(current) <= 0) {
                                    continue;
                                }
                                if (bestObjective == null || candidate.compareTo(bestObjective) > 0) {
                                    bestObjective = candidate;
                                    best = new Swap(
                                            firstStudent, firstTeam, secondStudent, secondTeam);
                                }
                            }
                        }
                    }
                }

                if (best == null) {
                    break;
                }
                applySwap(best);
                current = objective();
                passes++;
            }
        }

        private boolean canSwap(long first, int firstTeam, long second, int secondTeam) {
            Set<Long> firstForbidden = prohibitedByStudent.getOrDefault(first, Set.of());
            for (Long member : teamMembers.get(secondTeam)) {
                if (member != second && firstForbidden.contains(member)) {
                    return false;
                }
            }
            Set<Long> secondForbidden = prohibitedByStudent.getOrDefault(second, Set.of());
            for (Long member : teamMembers.get(firstTeam)) {
                if (member != first && secondForbidden.contains(member)) {
                    return false;
                }
            }

            for (MandatorySkill mandatorySkill : mandatorySkills) {
                long skillId = mandatorySkill.skillId();
                if (!teamCoversAfterSwap(firstTeam, first, second, skillId)
                        || !teamCoversAfterSwap(secondTeam, second, first, skillId)) {
                    return false;
                }
            }
            return true;
        }

        private boolean teamCoversAfterSwap(
                int team, long removedStudent, long addedStudent, long skillId) {
            if (qualifyingSkillsByStudent.getOrDefault(addedStudent, Set.of()).contains(skillId)) {
                return true;
            }
            for (Long member : teamMembers.get(team)) {
                if (member != removedStudent
                        && qualifyingSkillsByStudent.getOrDefault(member, Set.of()).contains(skillId)) {
                    return true;
                }
            }
            return false;
        }

        private FormationObjective evaluateSwap(
                long first, int firstTeam, long second, int secondTeam,
                FormationObjective current) {
            Set<Long> affected = new LinkedHashSet<>();
            affected.add(first);
            affected.add(second);
            affected.addAll(preferredBy.getOrDefault(first, List.of()));
            affected.addAll(preferredBy.getOrDefault(second, List.of()));

            double deltaScore = 0.0;
            int deltaUnsatisfied = 0;

            for (Long studentId : affected) {
                if (!teamOf.containsKey(studentId)) {
                    continue;
                }
                Map<Long, Double> outgoing =
                        outgoingWeights.getOrDefault(studentId, Map.of());
                if (outgoing.isEmpty()) {
                    continue;
                }
                double oldContribution = contribution(studentId, outgoing);
                teamOf.put(first, secondTeam);
                teamOf.put(second, firstTeam);
                double newContribution = contribution(studentId, outgoing);
                teamOf.put(first, firstTeam);
                teamOf.put(second, secondTeam);

                deltaScore += newContribution - oldContribution;
                boolean oldSatisfied = oldContribution > 0.0;
                boolean newSatisfied = newContribution > 0.0;
                if (oldSatisfied && !newSatisfied) {
                    deltaUnsatisfied++;
                } else if (!oldSatisfied && newSatisfied) {
                    deltaUnsatisfied--;
                }
            }

            long deltaTieBreak =
                    DeterministicTieBreaker.studentRank(input.seed(), first) * (secondTeam - firstTeam)
                            + DeterministicTieBreaker.studentRank(input.seed(), second)
                            * (firstTeam - secondTeam);

            return new FormationObjective(
                    current.totalPreferenceScore() + deltaScore,
                    current.unsatisfiedPreferenceStudents() + deltaUnsatisfied,
                    current.deterministicTieBreak() + deltaTieBreak);
        }

        private double contribution(long studentId, Map<Long, Double> outgoing) {
            Integer team = teamOf.get(studentId);
            if (team == null) {
                return 0.0;
            }
            double score = 0.0;
            for (Map.Entry<Long, Double> preference : outgoing.entrySet()) {
                if (team.equals(teamOf.get(preference.getKey()))) {
                    score += preference.getValue();
                }
            }
            return score;
        }

        private void applySwap(Swap swap) {
            List<Long> firstMembers = teamMembers.get(swap.firstTeam());
            List<Long> secondMembers = teamMembers.get(swap.secondTeam());
            firstMembers.remove(swap.firstStudent());
            secondMembers.remove(swap.secondStudent());
            firstMembers.add(swap.secondStudent());
            secondMembers.add(swap.firstStudent());
            firstMembers.sort(Long::compareTo);
            secondMembers.sort(Long::compareTo);
            teamOf.put(swap.firstStudent(), swap.secondTeam());
            teamOf.put(swap.secondStudent(), swap.firstTeam());
        }

        private FormationObjective objective() {
            List<Long> studentIds = orderedStudents.stream().map(FormationStudent::studentId).toList();
            double totalScore = 0.0;
            for (List<Long> members : teamMembers) {
                totalScore += CompatibilityScorer.scoreTeam(members, preferencesByStudent);
            }
            int unsatisfied = CompatibilityScorer.unsatisfiedPreferenceStudents(
                    studentIds, teamOf, preferencesByStudent);
            long tieBreak = DeterministicTieBreaker.planTieBreak(input.seed(), teamOf, studentIds);
            return new FormationObjective(totalScore, unsatisfied, tieBreak);
        }

        // ------------------------------------------------------------------
        // Immutable result
        // ------------------------------------------------------------------

        private FormationPlan toPlan() {
            List<Long> studentIds = orderedStudents.stream().map(FormationStudent::studentId).toList();

            record Snapshot(int originalOrdinal, List<Long> members, int size, long smallestMember) {
            }

            List<Snapshot> snapshots = new ArrayList<>(teamCount);
            for (int team = 0; team < teamCount; team++) {
                List<Long> members = new ArrayList<>(teamMembers.get(team));
                members.sort(Long::compareTo);
                long smallest = members.isEmpty() ? Long.MAX_VALUE : members.getFirst();
                snapshots.add(new Snapshot(team, List.copyOf(members), members.size(), smallest));
            }
            snapshots.sort(Comparator
                    .comparingInt(Snapshot::size).reversed()
                    .thenComparingLong(Snapshot::smallestMember)
                    .thenComparingInt(Snapshot::originalOrdinal));

            List<String> names = TeamNameGenerator.generate(snapshots.size());
            List<FormationTeam> teams = new ArrayList<>(snapshots.size());
            Map<Long, Integer> canonicalTeamOf = new HashMap<>();
            double totalScore = 0.0;
            for (int i = 0; i < snapshots.size(); i++) {
                Snapshot snapshot = snapshots.get(i);
                for (Long memberId : snapshot.members()) {
                    canonicalTeamOf.put(memberId, i);
                }
                double teamScore = CompatibilityScorer.scoreTeam(
                        snapshot.members(), preferencesByStudent);
                totalScore += teamScore;
                teams.add(new FormationTeam(
                        i,
                        names.get(i),
                        snapshot.size(),
                        snapshot.members(),
                        teamScore));
            }

            int unsatisfied = CompatibilityScorer.unsatisfiedPreferenceStudents(
                    studentIds, canonicalTeamOf, preferencesByStudent);
            int withoutPreferences = CompatibilityScorer.studentsWithoutPreferences(
                    studentIds, preferencesByStudent);
            long tieBreak = DeterministicTieBreaker.planTieBreak(
                    input.seed(), canonicalTeamOf, studentIds);

            return new FormationPlan(
                    teams,
                    totalScore,
                    unsatisfied,
                    withoutPreferences,
                    input.algorithmVersion(),
                    input.seed(),
                    tieBreak);
        }

        private record TeamChoice(int teamIndex, int coveredUnmet, double incremental, long teamRank) {
        }

        private record Swap(long firstStudent, int firstTeam, long secondStudent, int secondTeam) {
        }
    }
}
