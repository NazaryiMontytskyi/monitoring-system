package com.nmontytskyi.monitoring.server.sla;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.model.SlaDefinition;
import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.entity.SlaDefinitionEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Computes SLA compliance reports for registered microservices.
 *
 * <p>Only <em>reads</em> data — alert logic remains in {@code AlertEvaluationService}.
 * For each call it:
 * <ol>
 *   <li>resolves the requested time window (e.g. last 24 hours),</li>
 *   <li>queries aggregate counters and percentiles from {@code metric_records},</li>
 *   <li>builds a {@link SlaReport} with actual values and compliance flags.</li>
 * </ol>
 *
 * <p>When no {@link SlaDefinitionEntity} has been created for the service,
 * {@link SlaDefinition#defaults()} is used (99.9% uptime, 1 000 ms, 5% errors).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaCalculationService {

    private final MetricRecordRepository metricRecordRepository;
    private final RegisteredServiceRepository serviceRepository;

    /**
     * Calculates an SLA report for the given service over the specified window.
     *
     * @param serviceId identifier of the registered service
     * @param window    time window over which to aggregate metrics
     * @return a fully populated {@link SlaReport}
     * @throws ServiceNotFoundException if no service exists with {@code serviceId}
     */
    @Transactional(readOnly = true)
    public SlaReport calculate(Long serviceId, SlaWindow window) {
        RegisteredServiceEntity serviceEntity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        LocalDateTime since = LocalDateTime.now().minus(window.getDuration());
        LocalDateTime to    = LocalDateTime.now();

        long total    = metricRecordRepository.countByServiceIdSince(serviceId, since);
        long upCount  = metricRecordRepository
                            .countByServiceIdAndStatusSince(serviceId, since, HealthStatus.UP);
        long errors   = metricRecordRepository.countErrors(serviceId, since);
        Double avgMs  = metricRecordRepository.avgResponseTimeSince(serviceId, since);

        List<Object[]> pct = metricRecordRepository.findPercentiles(serviceId, since);

        double uptimePct    = total == 0 ? 100.0 : (upCount  * 100.0 / total);
        double errorRatePct = total == 0 ?   0.0 : (errors   * 100.0 / total);
        long   p50 = extractLong(pct, 0);
        long   p95 = extractLong(pct, 1);
        long   p99 = extractLong(pct, 2);

        SlaDefinition sla = resolveSla(serviceEntity);

        log.debug("SLA report for service {}: window={}, total={}, uptime={}%, errorRate={}%, avgMs={}",
                serviceId, window, total, uptimePct, errorRatePct, avgMs);

        return SlaReport.builder()
                .serviceId(String.valueOf(serviceId))
                .from(since)
                .to(to)
                .actualUptimePercent(uptimePct)
                .actualAvgResponseTimeMs(avgMs != null ? avgMs : 0.0)
                .actualErrorRatePercent(errorRatePct)
                .p50ResponseTimeMs(p50)
                .p95ResponseTimeMs(p95)
                .p99ResponseTimeMs(p99)
                .sla(sla)
                .uptimeMet(uptimePct    >= sla.getUptimePercent())
                .responseTimeMet(avgMs  == null || avgMs <= sla.getMaxResponseTimeMs())
                .errorRateMet(errorRatePct <= sla.getMaxErrorRatePercent())
                .build();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Returns a {@link SlaDefinition} from the service's persisted entity,
     * falling back to {@link SlaDefinition#defaults()} when none exists.
     */
    private SlaDefinition resolveSla(RegisteredServiceEntity serviceEntity) {
        SlaDefinitionEntity slaEntity = serviceEntity.getSlaDefinition();
        if (slaEntity == null) {
            return SlaDefinition.defaults();
        }
        return SlaDefinition.builder()
                .uptimePercent(slaEntity.getUptimePercent())
                .maxResponseTimeMs(slaEntity.getMaxResponseTimeMs())
                .maxErrorRatePercent(slaEntity.getMaxErrorRatePercent())
                .description(slaEntity.getDescription())
                .build();
    }

    /**
     * Extracts a {@code long} from the first row of the percentile result at the
     * given column index. Returns {@code 0} when the list is empty or the value
     * is {@code null} (i.e. no records exist in the window).
     *
     * <p>Uses {@code List<Object[]>} rather than {@code Optional<Object[]>}
     * because Spring Data JPA wraps multi-column native query rows in an extra
     * {@code Object[]} when using {@code Optional}, making flat index access
     * unreliable across Spring Data / Hibernate versions.
     */
    private long extractLong(List<Object[]> pct, int index) {
        if (pct.isEmpty()) {
            return 0L;
        }
        Object value = pct.get(0)[index];
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }
}
