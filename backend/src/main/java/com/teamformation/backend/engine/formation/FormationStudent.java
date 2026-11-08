package com.teamformation.backend.engine.formation;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of one eligible student used by the pure formation engine.
 * Keys are skill ids and values are proficiency levels 1 to 5.
 */
public record FormationStudent(long studentId, Map<Long, Integer> proficiencyBySkillId) {

    public FormationStudent {
        if (studentId <= 0) {
            throw new IllegalArgumentException("studentId must be positive");
        }
        proficiencyBySkillId = proficiencyBySkillId == null
                ? Map.of()
                : Map.copyOf(proficiencyBySkillId);
    }

    public boolean meets(long skillId, int minimumProficiency) {
        Integer proficiency = proficiencyBySkillId.get(skillId);
        return proficiency != null && proficiency >= minimumProficiency;
    }

    public int proficiency(long skillId) {
        return Objects.requireNonNullElse(proficiencyBySkillId.get(skillId), 0);
    }
}
