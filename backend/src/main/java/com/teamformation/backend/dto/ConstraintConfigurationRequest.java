package com.teamformation.backend.dto;

import java.util.List;

/**
 * Replaces the whole constraint configuration in one version. Omitted lists are
 * treated as empty.
 */
public record ConstraintConfigurationRequest(
        List<ProhibitedPairDto> prohibitedPairs,
        List<MandatorySkillDto> mandatorySkills
) {
}
