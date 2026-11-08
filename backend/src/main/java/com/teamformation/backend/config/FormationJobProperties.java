package com.teamformation.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Operational configuration for the Engine 3 formation-job lifecycle.
 *
 * <p>The stale timeout is the age after which a committed READY job (or a
 * legitimately committed RUNNING job) is considered abandoned and is failed by
 * the recovery reaper. The reaper interval and initial delay control the
 * scheduled sweep. The concurrency values bound synchronous runs per mentor and
 * across the whole application. The execution budget is an optional wall-clock
 * limit for one in-process search; reaching it fails the job with a stable
 * {@code EXECUTION_BUDGET_EXCEEDED} code.</p>
 *
 * <p>These values are operational, not algorithmic. They do not change any
 * scoring, hard-constraint, deterministic tie-break, or bounded-search
 * semantics.</p>
 */
@Validated
@ConfigurationProperties(prefix = "formation.jobs")
public record FormationJobProperties(
        @NotNull Duration staleTimeout,
        @NotNull Duration reaperInterval,
        @NotNull Duration reaperInitialDelay,
        @Min(1) int globalConcurrency,
        @Min(1) int perMentorConcurrency,
        @NotNull Duration executionBudget
) {

    public FormationJobProperties {
        if (staleTimeout == null || staleTimeout.isNegative() || staleTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "formation.jobs.stale-timeout must be greater than zero");
        }
        if (reaperInterval == null || reaperInterval.isNegative() || reaperInterval.isZero()) {
            throw new IllegalArgumentException(
                    "formation.jobs.reaper-interval must be greater than zero");
        }
        if (reaperInitialDelay == null || reaperInitialDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "formation.jobs.reaper-initial-delay must be zero or greater");
        }
        if (globalConcurrency < 1) {
            throw new IllegalArgumentException(
                    "formation.jobs.global-concurrency must be at least 1");
        }
        if (perMentorConcurrency < 1) {
            throw new IllegalArgumentException(
                    "formation.jobs.per-mentor-concurrency must be at least 1");
        }
        if (executionBudget == null || executionBudget.isNegative() || executionBudget.isZero()) {
            throw new IllegalArgumentException(
                    "formation.jobs.execution-budget must be greater than zero");
        }
    }
}
