package com.teamformation.backend.engine.constraint;

import java.util.List;

/**
 * A fully typed constraint configuration. The version is the stable number a
 * formation run can reference later. Task 2.6 supports only prohibited pairs
 * and mandatory skills.
 */
public record ConstraintConfiguration(
        int version,
        List<ProhibitedPair> prohibitedPairs,
        List<MandatorySkill> mandatorySkills
) {

    public ConstraintConfiguration {
        if (version < 0) {
            throw new IllegalArgumentException("version must be at least 0");
        }
        prohibitedPairs = prohibitedPairs == null ? List.of() : List.copyOf(prohibitedPairs);
        mandatorySkills = mandatorySkills == null ? List.of() : List.copyOf(mandatorySkills);
    }

    public static ConstraintConfiguration empty(int version) {
        return new ConstraintConfiguration(version, List.of(), List.of());
    }
}
