package com.teamformation.backend.engine.formation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic candidate-team naming. Names are {@code Team 01}, {@code Team
 * 02}, and so on. The zero-padded width is {@code max(2, digits(teamCount))}.
 */
public final class TeamNameGenerator {

    private TeamNameGenerator() {
    }

    public static String teamName(int position, int teamCount) {
        if (position < 0 || teamCount < 1 || position >= teamCount) {
            throw new IllegalArgumentException("position must be inside 0..teamCount-1");
        }
        int width = Math.max(2, String.valueOf(teamCount).length());
        return "Team " + String.format("%0" + width + "d", position + 1);
    }

    public static List<String> generate(int teamCount) {
        List<String> names = new ArrayList<>(Math.max(teamCount, 0));
        for (int i = 0; i < teamCount; i++) {
            names.add(teamName(i, teamCount));
        }
        return List.copyOf(names);
    }

    /**
     * Fails fast when a caller supplied duplicate names. The generator never
     * produces duplicates; this guard protects persistence from malformed plans.
     */
    public static void requireUnique(List<String> names) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("every candidate team needs a non-blank name");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate candidate team name: " + name);
            }
        }
    }
}
