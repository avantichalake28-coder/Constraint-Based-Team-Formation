package com.teamformation.backend.engine.constraint;

/**
 * A hard rule that every team must contain at least one student with the skill
 * at or above the minimum proficiency (1 to 5).
 */
public record MandatorySkill(Long skillId, int minimumProficiency) {

    public MandatorySkill {
        if (skillId == null) {
            throw new IllegalArgumentException("skill id is required");
        }
        if (minimumProficiency < 1 || minimumProficiency > 5) {
            throw new IllegalArgumentException("minimum proficiency must be between 1 and 5");
        }
    }
}
