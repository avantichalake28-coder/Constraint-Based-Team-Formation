package com.teamformation.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormationSearchPropertiesTest {

    @Test
    @DisplayName("Positive search limits are accepted")
    void constructor_acceptsPositiveLimits() {
        FormationSearchProperties properties = new FormationSearchProperties(250_000, 100);

        assertThat(properties.maxNodes()).isEqualTo(250_000);
        assertThat(properties.maxImprovementPasses()).isEqualTo(100);
    }

    @Test
    @DisplayName("Startup validation rejects a non-positive node budget")
    void constructor_rejectsNonPositiveMaxNodes() {
        assertThatThrownBy(() -> new FormationSearchProperties(0, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-nodes");
    }

    @Test
    @DisplayName("Startup validation rejects a non-positive improvement budget")
    void constructor_rejectsNonPositiveImprovementPasses() {
        assertThatThrownBy(() -> new FormationSearchProperties(250_000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-improvement-passes");
    }
}
