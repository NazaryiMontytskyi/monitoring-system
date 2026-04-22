package com.nmontytskyi.monitoring.server.sla;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.entity.SlaDefinitionEntity;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SlaCalculationService}.
 *
 * <p>All repository interactions are mocked — no database required.
 */
@ExtendWith(MockitoExtension.class)
class SlaCalculationServiceTest {

    @Mock
    private MetricRecordRepository metricRecordRepository;

    @Mock
    private RegisteredServiceRepository serviceRepository;

    @InjectMocks
    private SlaCalculationService slaCalculationService;

    private RegisteredServiceEntity serviceEntity;

    @BeforeEach
    void setUp() {
        // Service with no SlaDefinitionEntity → defaults will be used
        serviceEntity = RegisteredServiceEntity.builder()
                .id(1L)
                .name("test-service")
                .host("localhost")
                .port(8080)
                .status(HealthStatus.UNKNOWN)
                .build();
    }

    // ── 1. No records → 100% uptime, 0% error rate ───────────────────────────

    @Test
    void calculate_noRecords_returns100UptimeAnd0ErrorRate() {
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(0L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(0L);
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(0L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(null);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.DAY);

        assertThat(report.getActualUptimePercent()).isEqualTo(100.0);
        assertThat(report.getActualErrorRatePercent()).isEqualTo(0.0);
        assertThat(report.getActualAvgResponseTimeMs()).isEqualTo(0.0);
        // With no data, avgMs is null → responseTimeMet = true (can't be violated)
        assertThat(report.isUptimeMet()).isTrue();
        assertThat(report.isErrorRateMet()).isTrue();
        assertThat(report.isResponseTimeMet()).isTrue();
        assertThat(report.isSlaBreached()).isFalse();
    }

    // ── 2. All UP → uptime SLA met ────────────────────────────────────────────

    @Test
    void calculate_allUp_uptimeIsMet() {
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(100L);
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(0L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(200.0);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.DAY);

        assertThat(report.getActualUptimePercent()).isEqualTo(100.0);
        assertThat(report.isUptimeMet()).isTrue();
    }

    // ── 3. Some DOWN → uptime SLA violated ───────────────────────────────────

    @Test
    void calculate_someDown_uptimeViolated() {
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        // Only 95 out of 100 were UP → 95% uptime, below the 99.9% default SLA
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(95L);
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(5L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(200.0);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.DAY);

        assertThat(report.getActualUptimePercent()).isEqualTo(95.0);
        assertThat(report.isUptimeMet()).isFalse();  // 95.0 < 99.9
        assertThat(report.isSlaBreached()).isTrue();
    }

    // ── 4. High error rate → errorRateMet = false ────────────────────────────

    @Test
    void calculate_highErrorRate_errorRateMet_isFalse() {
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(100L);
        // 10 errors out of 100 = 10% error rate, exceeds default 5% SLA
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(10L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(200.0);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.DAY);

        assertThat(report.getActualErrorRatePercent()).isEqualTo(10.0);
        assertThat(report.isErrorRateMet()).isFalse(); // 10.0 > 5.0
    }

    // ── 5. High avg response → responseTimeMet = false ───────────────────────

    @Test
    void calculate_highAvgResponse_responseTimeMet_isFalse() {
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(100L);
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(0L);
        // 2 000 ms average exceeds the default 1 000 ms SLA threshold
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(2000.0);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.DAY);

        assertThat(report.getActualAvgResponseTimeMs()).isEqualTo(2000.0);
        assertThat(report.isResponseTimeMet()).isFalse(); // 2000 > 1000
    }

    // ── 6. No SlaDefinitionEntity → defaults applied ─────────────────────────

    @Test
    void calculate_usesDefaultSla_whenEntityMissing() {
        // serviceEntity has getSlaDefinition() == null (no SlaDefinitionEntity set)
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(10L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(10L);
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(0L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(100.0);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.HOUR);

        assertThat(report.getSla()).isNotNull();
        assertThat(report.getSla().getUptimePercent()).isEqualTo(99.9);       // default
        assertThat(report.getSla().getMaxResponseTimeMs()).isEqualTo(1000L);  // default
        assertThat(report.getSla().getMaxErrorRatePercent()).isEqualTo(5.0);  // default
        assertThat(report.getServiceId()).isEqualTo("1");
    }

    // ── 7. Custom SlaDefinitionEntity is used when present ───────────────────

    @Test
    void calculate_usesCustomSla_whenEntityPresent() {
        SlaDefinitionEntity customSla = SlaDefinitionEntity.builder()
                .uptimePercent(95.0)
                .maxResponseTimeMs(500L)
                .maxErrorRatePercent(1.0)
                .description("Relaxed SLA")
                .build();
        serviceEntity.setSlaDefinition(customSla);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(serviceEntity));
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(96L); // 96% uptime — meets 95% SLA
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(0L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(400.0);
        when(metricRecordRepository.findPercentiles(eq(1L), any())).thenReturn(Collections.emptyList());

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.WEEK);

        assertThat(report.getSla().getUptimePercent()).isEqualTo(95.0);
        assertThat(report.isUptimeMet()).isTrue(); // 96.0 >= 95.0
        assertThat(report.isResponseTimeMet()).isTrue(); // 400 <= 500
    }

    // ── 8. Percentile values are extracted correctly ──────────────────────────

    @Test
    void calculate_percentiles_arePopulatedFromRepository() {
        givenServiceExists();
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(50L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP)))
                .thenReturn(50L);
        when(metricRecordRepository.countErrors(eq(1L), any())).thenReturn(0L);
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any())).thenReturn(200.0);

        Object[] pctRow = {150.0, 450.0, 900.0}; // p50, p95, p99
        when(metricRecordRepository.findPercentiles(eq(1L), any()))
                .thenReturn(Collections.singletonList(pctRow));

        SlaReport report = slaCalculationService.calculate(1L, SlaWindow.DAY);

        assertThat(report.getP50ResponseTimeMs()).isEqualTo(150L);
        assertThat(report.getP95ResponseTimeMs()).isEqualTo(450L);
        assertThat(report.getP99ResponseTimeMs()).isEqualTo(900L);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private void givenServiceExists() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(serviceEntity));
    }
}
