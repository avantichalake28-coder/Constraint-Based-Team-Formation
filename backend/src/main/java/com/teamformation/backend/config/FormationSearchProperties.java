package com.teamformation.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded-search configuration for Engine 3. Both limits must be positive;
 * startup validation rejects zero or negative values.
 *
 * @param maxNodes              maximum number of placement attempts explored by
 *                              the backtracking search
 * @param maxImprovementPasses  maximum number of accepted pairwise swaps in the
 *                              local-search phase
 */
@Validated
@ConfigurationProperties(prefix = "formation.search")
public record FormationSearchProperties(
        @Min(1) int maxNodes,
        @Min(1) int maxImprovementPasses
) {

    public FormationSearchProperties {
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("formation.search.max-nodes must be greater than zero");
        }
        if (maxImprovementPasses <= 0) {
            throw new IllegalArgumentException(
                    "formation.search.max-improvement-passes must be greater than zero");
        }
    }
}
