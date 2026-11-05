package com.teamformation.backend.engine.constraint;

import java.util.Map;

/**
 * Pure snapshot of one rostered student's skill proficiency. Keys are skill ids
 * and values are proficiency levels 1 to 5.
 */
public record RosterStudentSkills(long studentId, Map<Long, Integer> proficiencyBySkillId) {

    public RosterStudentSkills {
        proficiencyBySkillId = proficiencyBySkillId == null ? Map.of() : Map.copyOf(proficiencyBySkillId);
    }
}
