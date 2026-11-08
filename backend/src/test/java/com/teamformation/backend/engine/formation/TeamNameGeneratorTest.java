package com.teamformation.backend.engine.formation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamNameGeneratorTest {

    @Test
    @DisplayName("The width is at least two digits")
    void generate_padsToTwoDigits() {
        assertThat(TeamNameGenerator.generate(9))
                .containsExactly("Team 01", "Team 02", "Team 03", "Team 04",
                        "Team 05", "Team 06", "Team 07", "Team 08", "Team 09");
    }

    @Test
    @DisplayName("The width grows to the digit count of the team count")
    void generate_growsWithTeamCount() {
        List<String> names = TeamNameGenerator.generate(100);

        assertThat(names).hasSize(100);
        assertThat(names.get(0)).isEqualTo("Team 001");
        assertThat(names.get(99)).isEqualTo("Team 100");
    }

    @Test
    @DisplayName("Duplicate or blank names are rejected before persistence")
    void requireUnique_rejectsDuplicatesAndBlanks() {
        assertThatThrownBy(() -> TeamNameGenerator.requireUnique(List.of("Team 01", "Team 01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TeamNameGenerator.requireUnique(List.of("Team 01", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
