package com.nmontytskyi.monitoring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MetricSnapshot")
class MetricSnapshotTest {

    // ── Default values ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("default values")
    class DefaultValueTests {

        @Test
        @DisplayName("anomaly defaults to false")
        void builder_anomalyDefaultIsFalse() {
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("order-service")
                    .status(HealthStatus.UP)
                    .build();

            assertThat(snapshot.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("zScore defaults to 0.0")
        void builder_zScoreDefaultIsZero() {
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("order-service")
                    .status(HealthStatus.UP)
                    .build();

            assertThat(snapshot.getZScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("endpoint defaults to null (represents overall service snapshot)")
        void builder_endpointDefaultIsNull() {
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("order-service")
                    .build();

            assertThat(snapshot.getEndpoint()).isNull();
        }
    }

    // ── Anomaly fields ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("anomaly fields")
    class AnomalyFieldTests {

        @Test
        @DisplayName("anomaly and zScore are stored correctly when set explicitly")
        void builder_withAnomalyFields_setsCorrectly() {
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("payment-service")
                    .status(HealthStatus.DEGRADED)
                    .anomaly(true)
                    .zScore(4.5)
                    .build();

            assertThat(snapshot.isAnomaly()).isTrue();
            assertThat(snapshot.getZScore()).isEqualTo(4.5);
        }

        @Test
        @DisplayName("negative zScore is stored correctly (value below normal range)")
        void builder_withNegativeZScore_setsCorrectly() {
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("inventory-service")
                    .anomaly(true)
                    .zScore(-3.8)
                    .build();

            assertThat(snapshot.getZScore()).isEqualTo(-3.8);
            assertThat(snapshot.isAnomaly()).isTrue();
        }
    }

    // ── Full snapshot ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("full snapshot")
    class FullSnapshotTests {

        @Test
        @DisplayName("all fields are stored correctly via builder")
        void builder_setsAllFields() {
            LocalDateTime now = LocalDateTime.now();

            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("order-service")
                    .endpoint("POST /orders")
                    .responseTimeMs(124L)
                    .status(HealthStatus.UP)
                    .cpuUsage(0.12)
                    .heapUsedMb(245L)
                    .heapMaxMb(512L)
                    .errorMessage(null)
                    .recordedAt(now)
                    .anomaly(false)
                    .zScore(0.5)
                    .build();

            assertThat(snapshot.getServiceId()).isEqualTo("order-service");
            assertThat(snapshot.getEndpoint()).isEqualTo("POST /orders");
            assertThat(snapshot.getResponseTimeMs()).isEqualTo(124L);
            assertThat(snapshot.getStatus()).isEqualTo(HealthStatus.UP);
            assertThat(snapshot.getCpuUsage()).isEqualTo(0.12);
            assertThat(snapshot.getHeapUsedMb()).isEqualTo(245L);
            assertThat(snapshot.getHeapMaxMb()).isEqualTo(512L);
            assertThat(snapshot.getErrorMessage()).isNull();
            assertThat(snapshot.getRecordedAt()).isEqualTo(now);
            assertThat(snapshot.isAnomaly()).isFalse();
            assertThat(snapshot.getZScore()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("DOWN snapshot carries an error message")
        void builder_downStatusWithErrorMessage() {
            MetricSnapshot snapshot = MetricSnapshot.builder()
                    .serviceId("payment-service")
                    .status(HealthStatus.DOWN)
                    .errorMessage("Connection refused: localhost:8082")
                    .build();

            assertThat(snapshot.getStatus()).isEqualTo(HealthStatus.DOWN);
            assertThat(snapshot.getErrorMessage()).contains("Connection refused");
        }
    }

    // ── HealthStatus compatibility ────────────────────────────────────────────

    @Nested
    @DisplayName("HealthStatus compatibility")
    class HealthStatusCompatibilityTests {

        @Test
        @DisplayName("accepts UP status")
        void builder_withUpStatus() {
            assertThat(MetricSnapshot.builder().status(HealthStatus.UP).build().getStatus())
                    .isEqualTo(HealthStatus.UP);
        }

        @Test
        @DisplayName("accepts DEGRADED status")
        void builder_withDegradedStatus() {
            assertThat(MetricSnapshot.builder().status(HealthStatus.DEGRADED).build().getStatus())
                    .isEqualTo(HealthStatus.DEGRADED);
        }

        @Test
        @DisplayName("accepts UNKNOWN status for newly registered services without data")
        void builder_withUnknownStatus() {
            assertThat(MetricSnapshot.builder().status(HealthStatus.UNKNOWN).build().getStatus())
                    .isEqualTo(HealthStatus.UNKNOWN);
        }
    }
}
