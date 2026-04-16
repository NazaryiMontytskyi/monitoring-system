package com.nmontytskyi.monitoring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HealthStatus")
class HealthStatusTest {

    @Test
    @DisplayName("contains exactly 4 values: UP, DEGRADED, DOWN, UNKNOWN")
    void values_containsAllExpectedStatuses() {
        assertThat(HealthStatus.values())
                .containsExactlyInAnyOrder(
                        HealthStatus.UP,
                        HealthStatus.DEGRADED,
                        HealthStatus.DOWN,
                        HealthStatus.UNKNOWN
                );
    }

    @Test
    @DisplayName("valueOf returns the correct enum constant by name")
    void valueOf_returnsCorrectEnumByName() {
        assertThat(HealthStatus.valueOf("UP")).isEqualTo(HealthStatus.UP);
        assertThat(HealthStatus.valueOf("DEGRADED")).isEqualTo(HealthStatus.DEGRADED);
        assertThat(HealthStatus.valueOf("DOWN")).isEqualTo(HealthStatus.DOWN);
        assertThat(HealthStatus.valueOf("UNKNOWN")).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    @DisplayName("valueOf throws exception for an unknown name")
    void valueOf_withUnknownName_throwsException() {
        assertThatThrownBy(() -> HealthStatus.valueOf("OFFLINE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UP has ordinal 0 (first in the declaration)")
    void up_hasOrdinalZero() {
        assertThat(HealthStatus.UP.ordinal()).isEqualTo(0);
    }

    @Test
    @DisplayName("name() returns the string representation of the status")
    void name_returnsStringRepresentation() {
        assertThat(HealthStatus.DOWN.name()).isEqualTo("DOWN");
        assertThat(HealthStatus.DEGRADED.name()).isEqualTo("DEGRADED");
    }
}
