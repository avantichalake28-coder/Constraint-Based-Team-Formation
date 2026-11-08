package com.teamformation.backend.engine.formation;

import java.time.Instant;

/**
 * Immutable search budgets used by the pure formation engine.
 *
 * @param maxNodes             maximum number of placement attempts
 * @param maxImprovementPasses maximum number of accepted local-search swaps
 * @param deadline             optional wall-clock deadline; {@code null} means no
 *                             time budget. The engine checks it between search
 *                             steps and throws
 *                             {@link FormationExecutionBudgetExceededException}
 *                             when it has passed.
 */
public record FormationSearchLimits(int maxNodes, int maxImprovementPasses, Instant deadline) {

    public FormationSearchLimits {
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be greater than zero");
        }
        if (maxImprovementPasses <= 0) {
            throw new IllegalArgumentException("maxImprovementPasses must be greater than zero");
        }
    }

    /**
     * Compatibility constructor for callers that do not need a wall-clock
     * budget. It is deliberately kept so the pure engine tests and any external
     * caller remain source-compatible.
     */
    public FormationSearchLimits(int maxNodes, int maxImprovementPasses) {
        this(maxNodes, maxImprovementPasses, null);
    }

    public boolean hasDeadline() {
        return deadline != null;
    }

    public boolean isExpired(Instant now) {
        return deadline != null && !now.isBefore(deadline);
    }
}
