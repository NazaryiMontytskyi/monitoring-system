package com.nmontytskyi.monitoring.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@DisplayName("SlaReport")
class SlaReportTest {

    private SlaDefinition sla;
    private LocalDateTime from;
    private LocalDateTime to;

    @BeforeEach
    void setUp() {
        sla  = SlaDefinition.defaults();
        from = LocalDateTime.now().minusHours(1);
        to   = LocalDateTime.now();
    }

    // ── isSlaBreached ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isSlaBreached")
    class IsSlaBreachedTests {

        @Test
        @DisplayName("returns false when all SLA requirements are met")
        void isSlaBreached_whenAllRequirementsMet_returnsFalse() {
            SlaReport report = reportWith(true, true, true);
            assertThat(report.isSlaBreached()).isFalse();
        }

        @Test
        @DisplayName("returns true when uptime is below the required threshold")
        void isSlaBreached_whenUptimeNotMet_returnsTrue() {
            SlaReport report = reportWith(false, true, true);
            assertThat(report.isSlaBreached()).isTrue();
        }

        @Test
        @DisplayName("returns true when response time exceeds the required threshold")
        void isSlaBreached_whenResponseTimeNotMet_returnsTrue() {
            SlaReport report = reportWith(true, false, true);
            assertThat(report.isSlaBreached()).isTrue();
        }

        @Test
        @DisplayName("returns true when error rate exceeds the required threshold")
        void isSlaBreached_whenErrorRateNotMet_returnsTrue() {
            SlaReport report = reportWith(true, true, false);
            assertThat(report.isSlaBreached()).isTrue();
        }

        @Test
        @DisplayName("returns true when no SLA requirements are met")
        void isSlaBreached_whenNoneAreMet_returnsTrue() {
            SlaReport report = reportWith(false, false, false);
            assertThat(report.isSlaBreached()).isTrue();
        }
    }

    // ── getCompliancePercent ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getCompliancePercent")
    class GetCompliancePercentTests {

        @Test
        @DisplayName("returns 100.0 when all three requirements are met")
        void getCompliancePercent_whenAllMet_returns100() {
            SlaReport report = reportWith(true, true, true);
            assertThat(report.getCompliancePercent()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("returns 0.0 when no requirements are met")
        void getCompliancePercent_whenNoneMet_returns0() {
            SlaReport report = reportWith(false, false, false);
            assertThat(report.getCompliancePercent()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns ~66.67% when two out of three requirements are met")
        void getCompliancePercent_whenTwoOfThreeMet_returnsApprox67() {
            SlaReport report = reportWith(true, true, false);
            assertThat(report.getCompliancePercent()).isCloseTo(66.67, offset(0.01));
        }

        @Test
        @DisplayName("returns ~33.33% when one out of three requirements is met")
        void getCompliancePercent_whenOneOfThreeMet_returnsApprox33() {
            SlaReport report = reportWith(true, false, false);
            assertThat(report.getCompliancePercent()).isCloseTo(33.33, offset(0.01));
        }
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("stores all fields correctly")
        void builder_setsAllFields() {
            SlaReport report = SlaReport.builder()
                    .serviceId("order-service")
                    .from(from)
                    .to(to)
                    .actualUptimePercent(99.5)
                    .actualAvgResponseTimeMs(180.0)
                    .actualErrorRatePercent(0.3)
                    .p50ResponseTimeMs(120L)
                    .p95ResponseTimeMs(280L)
                    .p99ResponseTimeMs(950L)
                    .sla(sla)
                    .uptimeMet(true)
                    .responseTimeMet(true)
                    .errorRateMet(true)
                    .build();

            assertThat(report.getServiceId()).isEqualTo("order-service");
            assertThat(report.getFrom()).isEqualTo(from);
            assertThat(report.getTo()).isEqualTo(to);
            assertThat(report.getActualUptimePercent()).isEqualTo(99.5);
            assertThat(report.getActualAvgResponseTimeMs()).isEqualTo(180.0);
            assertThat(report.getActualErrorRatePercent()).isEqualTo(0.3);
            assertThat(report.getP50ResponseTimeMs()).isEqualTo(120L);
            assertThat(report.getP95ResponseTimeMs()).isEqualTo(280L);
            assertThat(report.getP99ResponseTimeMs()).isEqualTo(950L);
            assertThat(report.getSla()).isEqualTo(sla);
        }

        @Test
        @DisplayName("P50 <= P95 <= P99 in the built report")
        void builder_percentilesAreOrdered() {
            SlaReport report = SlaReport.builder()
                    .p50ResponseTimeMs(120L)
                    .p95ResponseTimeMs(280L)
                    .p99ResponseTimeMs(950L)
                    .build();

            assertThat(report.getP50ResponseTimeMs()).isLessThanOrEqualTo(report.getP95ResponseTimeMs());
            assertThat(report.getP95ResponseTimeMs()).isLessThanOrEqualTo(report.getP99ResponseTimeMs());
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private SlaReport reportWith(boolean uptimeMet, boolean responseTimeMet, boolean errorRateMet) {
        return SlaReport.builder()
                .serviceId("test-service")
                .from(from)
                .to(to)
                .sla(sla)
                .uptimeMet(uptimeMet)
                .responseTimeMet(responseTimeMet)
                .errorRateMet(errorRateMet)
                .build();
    }
}
