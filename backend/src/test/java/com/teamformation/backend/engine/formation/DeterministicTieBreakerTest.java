package com.teamformation.backend.engine.formation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicTieBreakerTest {

    @Test
    @DisplayName("The same seed and id always produce the same non-negative rank")
    void studentRank_isStableAndNonNegative() {
        long first = DeterministicTieBreaker.studentRank(42L, 7L);

        assertThat(DeterministicTieBreaker.studentRank(42L, 7L)).isEqualTo(first);
        assertThat(first).isGreaterThanOrEqualTo(0L);
        assertThat(DeterministicTieBreaker.studentRank(43L, 7L)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("Team ranks depend only on the seed and the ordinal")
    void teamRank_isStable() {
        assertThat(DeterministicTieBreaker.teamRank(9L, 0))
                .isEqualTo(DeterministicTieBreaker.teamRank(9L, 0));
        assertThat(DeterministicTieBreaker.teamRank(9L, 0))
                .isNotEqualTo(DeterministicTieBreaker.teamRank(9L, 1));
    }

    @Test
    @DisplayName("The plan tie-break is the sum of student rank times team ordinal")
    void planTieBreak_isDeltaComputable() {
        long seed = 17L;
        Map<Long, Integer> teamOf = Map.of(1L, 0, 2L, 1, 3L, 1, 4L, 0);

        long expected = DeterministicTieBreaker.studentRank(seed, 1L) * 0
                + DeterministicTieBreaker.studentRank(seed, 2L) * 1
                + DeterministicTieBreaker.studentRank(seed, 3L) * 1
                + DeterministicTieBreaker.studentRank(seed, 4L) * 0;

        assertThat(DeterministicTieBreaker.planTieBreak(
                seed, teamOf, List.of(1L, 2L, 3L, 4L))).isEqualTo(expected);
    }
}
