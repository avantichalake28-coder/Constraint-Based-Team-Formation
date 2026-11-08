package com.teamformation.backend.engine.formation;

import com.teamformation.backend.engine.constraint.MandatorySkill;
import com.teamformation.backend.engine.constraint.ProhibitedPair;
import com.teamformation.backend.engine.constraint.TeamSizePlan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete immutable input to the pure formation engine. It contains no JPA
 * entity, repository, controller or transaction reference.
 *
 * @param projectId         project being formed
 * @param planner           exact team-size plan produced by Engine 2
 * @param minTeamSize       configured minimum regular team size
 * @param maxTeamSize       configured maximum regular team size
 * @param teamSizeSlack     configured slack used by the planner
 * @param constraintVersion recorded constraint version (0 when none exists)
 * @param seed              run seed used by every deterministic tie-break
 * @param algorithmVersion  version label recorded with the run
 * @param students          eligible students, sorted by student id by the loader
 * @param preferences       all project preferences, in deterministic order
 * @param prohibitedPairs   supported hard prohibited pairs
 * @param mandatorySkills   supported hard mandatory skills
 * @param skillNames        names of the mandatory skills (for reporting only)
 */
public record FormationInput(
        Long projectId,
        TeamSizePlan planner,
        int minTeamSize,
        int maxTeamSize,
        int teamSizeSlack,
        int constraintVersion,
        long seed,
        String algorithmVersion,
        List<FormationStudent> students,
        List<FormationPreference> preferences,
        List<ProhibitedPair> prohibitedPairs,
        List<MandatorySkill> mandatorySkills,
        Map<Long, String> skillNames
) {

    public FormationInput {
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(planner, "planner is required");
        if (minTeamSize < 2) {
            throw new IllegalArgumentException("minTeamSize must be at least 2");
        }
        if (maxTeamSize < minTeamSize) {
            throw new IllegalArgumentException("maxTeamSize must be at least minTeamSize");
        }
        if (teamSizeSlack < 0) {
            throw new IllegalArgumentException("teamSizeSlack must be at least 0");
        }
        if (constraintVersion < 0) {
            throw new IllegalArgumentException("constraintVersion must be at least 0");
        }
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion is required");
        }
        students = students == null ? List.of() : List.copyOf(students);
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        prohibitedPairs = prohibitedPairs == null ? List.of() : List.copyOf(prohibitedPairs);
        mandatorySkills = mandatorySkills == null ? List.of() : List.copyOf(mandatorySkills);
        skillNames = skillNames == null ? Map.of() : Map.copyOf(skillNames);
    }

    public List<Integer> teamSizes() {
        return planner.teamSizes();
    }

    public int eligibleStudentCount() {
        return students.size();
    }
}
