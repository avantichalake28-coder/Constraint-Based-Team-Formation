package com.teamformation.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Current constraint configuration including the stable version number and the
 * actor/time of the change that produced it.
 */
public record ConstraintConfigurationResponse(
        int version,
        List<ProhibitedPairDto> prohibitedPairs,
        List<MandatorySkillDto> mandatorySkills,
        Long changedById,
        String changedByName,
        Instant changedAt
) {
}
