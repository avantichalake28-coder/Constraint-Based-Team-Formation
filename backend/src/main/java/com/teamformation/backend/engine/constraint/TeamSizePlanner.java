package com.teamformation.backend.engine.constraint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure team-size planner for Engine 2.
 *
 * <p>The planner never changes its inputs and never rebalances a configuration
 * silently. It applies the greedy rule defined for the project: form as many
 * full teams of {@code max} as possible, then either add the remainder as one
 * team, absorb the remainder into one full team when slack allows it, or report
 * infeasibility with suggestions that are not applied.</p>
 */
public final class TeamSizePlanner {

    private static final int MAX_SUGGESTIONS = 5;

    private TeamSizePlanner() {
    }

    /**
     * Plans team sizes.
     *
     * @param population number of eligible students, at least zero
     * @param min        smallest allowed regular team size, at least 2
     * @param max        largest regular team size, at least {@code min}
     * @param slack      extra students one full team may absorb, at least zero
     * @throws IllegalArgumentException when an input is outside its valid range
     */
    public static TeamSizePlan plan(int population, int min, int max, int slack) {
        validate(population, min, max, slack);

        if (population == 0) {
            return TeamSizePlan.infeasible(
                    PlannerInfeasibilityReason.EMPTY_POPULATION,
                    "There are no eligible students, so no team can be formed.",
                    List.of());
        }

        int fullTeams = population / max;
        int remainder = population % max;

        if (remainder == 0) {
            return TeamSizePlan.feasible(repeated(max, fullTeams), false);
        }

        if (remainder >= min) {
            List<Integer> sizes = repeated(max, fullTeams);
            sizes.add(remainder);
            sizes.sort(Comparator.reverseOrder());
            return TeamSizePlan.feasible(sizes, false);
        }

        if (fullTeams > 0 && remainder <= slack) {
            // Absorb the remainder into one full team instead of leaving a
            // team below the minimum. Slack is explicitly marked as used.
            List<Integer> sizes = repeated(max, fullTeams - 1);
            sizes.add(max + remainder);
            sizes.sort(Comparator.reverseOrder());
            return TeamSizePlan.feasible(sizes, true);
        }

        PlannerInfeasibilityReason reason = fullTeams == 0
                ? PlannerInfeasibilityReason.BELOW_MINIMUM
                : PlannerInfeasibilityReason.REMAINDER_NOT_PLACEABLE;
        String explanation = fullTeams == 0
                ? "The population of " + population + " is smaller than the minimum team size of " + min + "."
                : population + " students form " + fullTeams + " full team(s) of " + max
                        + " with a remainder of " + remainder
                        + ", which cannot form a team of at least " + min
                        + " and cannot be absorbed because the slack is " + slack + ".";
        return TeamSizePlan.infeasible(
                reason, explanation, findAlternatives(population, min, max, slack));
    }

    private static void validate(int population, int min, int max, int slack) {
        if (population < 0) {
            throw new IllegalArgumentException("population must be at least 0");
        }
        if (min < 2) {
            throw new IllegalArgumentException("min team size must be at least 2");
        }
        if (min > max) {
            throw new IllegalArgumentException("min team size must be less than or equal to max team size");
        }
        if (slack < 0) {
            throw new IllegalArgumentException("team size slack must be at least 0");
        }
    }

    private static List<Integer> repeated(int size, int count) {
        List<Integer> sizes = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            sizes.add(size);
        }
        return sizes;
    }

    /**
     * Finds a small, deterministic set of alternative valid splits. A split is
     * valid when every team is at least {@code min}, at most one team may be
     * above {@code max} by no more than {@code slack}, and the sizes sum to the
     * population. This is a suggestion only; it is never applied by the planner.
     */
    private static List<List<Integer>> findAlternatives(int population, int min, int max, int slack) {
        List<List<Integer>> suggestions = new ArrayList<>();
        int upperBound = max + slack;
        search(population, min, max, slack, upperBound, new ArrayList<>(), suggestions);
        return suggestions;
    }

    private static void search(
            int remaining,
            int min,
            int max,
            int slack,
            int upperBound,
            List<Integer> current,
            List<List<Integer>> suggestions) {
        if (suggestions.size() >= MAX_SUGGESTIONS) {
            return;
        }
        if (remaining == 0) {
            if (!current.isEmpty()) {
                suggestions.add(new ArrayList<>(current));
            }
            return;
        }
        if (remaining < min) {
            return;
        }

        int largest = Math.min(upperBound, remaining);
        for (int size = largest; size >= min; size--) {
            if (size > max) {
                if (slack <= 0) {
                    continue;
                }
                boolean alreadyAboveMax = current.stream().anyMatch(existing -> existing > max);
                if (alreadyAboveMax) {
                    continue;
                }
            }
            current.add(size);
            search(remaining - size, min, max, slack, upperBound, current, suggestions);
            current.remove(current.size() - 1);
            if (suggestions.size() >= MAX_SUGGESTIONS) {
                return;
            }
        }
    }

    /**
     * Defensive equality helper for callers that compare plans in tests.
     */
    public static boolean sameSizes(TeamSizePlan left, TeamSizePlan right) {
        return Objects.equals(left.teamSizes(), right.teamSizes())
                && left.slackUsed() == right.slackUsed()
                && left.feasible() == right.feasible();
    }
}
