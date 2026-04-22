package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


/**
 * Spring Data JPA repository for {@link MetricRecordEntity}.
 *
 * <p>This is the most query-intensive repository in the system:
 * it serves time-range queries for the dashboard (FR-3), provides the sliding
 * window of recent records for anomaly detection (FR-1), and feeds aggregate
 * calculations for the SLA report and REST API (FR-2).
 *
 * <p>The composite index {@code (service_id, recorded_at DESC)} on the underlying
 * table makes all range queries here efficient even with millions of rows.
 */
@Repository
public interface MetricRecordRepository extends JpaRepository<MetricRecordEntity, Long> {

    /**
     * Returns the N most recent metric records for a service, ordered newest first.
     * Used by {@code AnomalyDetector} to build the sliding window for Z-score calculation.
     *
     * @param serviceId the service identifier
     * @return up to 100 most recent records
     */
    List<MetricRecordEntity> findTop100ByServiceIdOrderByRecordedAtDesc(Long serviceId);

    /**
     * Returns all metric records for a service within a time range.
     * Used by the REST aggregate endpoint ({@code /api/metrics/{serviceId}/aggregate}).
     *
     * @param serviceId the service identifier
     * @param from      start of the time window (inclusive)
     * @param to        end of the time window (inclusive)
     * @return records ordered oldest first
     */
    List<MetricRecordEntity> findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long serviceId,
            LocalDateTime from,
            LocalDateTime to
    );

    /**
     * Returns the most recent metric record for a service regardless of source.
     * Used by the service detail page to display the current state.
     *
     * @param serviceId the service identifier
     * @return the latest record, if any
     */
    Optional<MetricRecordEntity> findTopByServiceIdOrderByRecordedAtDesc(Long serviceId);

    /**
     * Calculates aggregate metrics for a service over a time window.
     *
     * <p>Returns a single row with:
     * <ul>
     *   <li>average, minimum, and maximum {@code response_time_ms}</li>
     *   <li>total number of records in the window</li>
     *   <li>number of records where {@code status} is {@code 'UP'}</li>
     * </ul>
     *
     * <p>The caller computes uptime % as {@code (upCount / total) * 100}.
     *
     * @param serviceId the service identifier
     * @param from      start of the time window
     * @param to        end of the time window
     * @return single-element list containing an {@code Object[]} array:
     *         {@code [avgMs, minMs, maxMs, total, upCount]};
     *         empty list if no records exist in the window
     */
    @Query("""
            SELECT
                AVG(m.responseTimeMs),
                MIN(m.responseTimeMs),
                MAX(m.responseTimeMs),
                COUNT(m.id),
                SUM(CASE WHEN m.status = 'UP' THEN 1 ELSE 0 END)
            FROM MetricRecordEntity m
            WHERE m.service.id = :serviceId
              AND m.recordedAt BETWEEN :from AND :to
            """)
    List<Object[]> aggregateByServiceAndPeriod(
            @Param("serviceId") Long serviceId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Counts records per endpoint for a service over a time window.
     * Used by the endpoint breakdown table on the service detail page (FR-3).
     *
     * @param serviceId the service identifier
     * @param from      start of the time window
     * @param to        end of the time window
     * @return list of {@code [endpoint, count, avgResponseTimeMs]} arrays
     */
    @Query("""
            SELECT m.endpoint, COUNT(m.id), AVG(m.responseTimeMs)
            FROM MetricRecordEntity m
            WHERE m.service.id = :serviceId
              AND m.endpoint IS NOT NULL
              AND m.recordedAt BETWEEN :from AND :to
            GROUP BY m.endpoint
            ORDER BY COUNT(m.id) DESC
            """)
    List<Object[]> countByEndpointAndPeriod(
            @Param("serviceId") Long serviceId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT AVG(m.responseTimeMs) FROM MetricRecordEntity m " +
           "WHERE m.service.id = :serviceId AND m.recordedAt >= :since")
    Double avgResponseTimeSince(@Param("serviceId") Long serviceId,
                                @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(m) FROM MetricRecordEntity m " +
           "WHERE m.service.id = :serviceId AND m.recordedAt >= :since " +
           "AND m.status = :status")
    long countByServiceIdAndStatusSince(@Param("serviceId") Long serviceId,
                                        @Param("since") LocalDateTime since,
                                        @Param("status") HealthStatus status);

    @Query("SELECT COUNT(m) FROM MetricRecordEntity m " +
           "WHERE m.service.id = :serviceId AND m.recordedAt >= :since")
    long countByServiceIdSince(@Param("serviceId") Long serviceId,
                               @Param("since") LocalDateTime since);

    /**
     * Calculates the P50, P95, and P99 response-time percentiles for a service
     * over a sliding time window using PostgreSQL's {@code percentile_cont} function.
     *
     * <p>Uses a native query because JPQL does not support ordered-set aggregate
     * functions ({@code percentile_cont … WITHIN GROUP (ORDER BY …)}).
     *
     * <p>When no records match the filter (empty window), PostgreSQL returns
     * a single row with {@code NULL} values for all three percentile columns.
     *
     * @param serviceId the service identifier
     * @param since     start of the time window (inclusive)
     * @return single-element list containing an {@code Object[]} row
     *         {@code [p50, p95, p99]}; the values are {@code null} when no
     *         records exist in the window.
     *         Returns {@code List.of(new Object[]{null,null,null})} on empty input
     *         (PostgreSQL ordered-set aggregates always return one row).
     */
    @Query(value = """
            SELECT
                percentile_cont(0.50) WITHIN GROUP (ORDER BY response_time_ms) AS p50,
                percentile_cont(0.95) WITHIN GROUP (ORDER BY response_time_ms) AS p95,
                percentile_cont(0.99) WITHIN GROUP (ORDER BY response_time_ms) AS p99
            FROM metric_records
            WHERE service_id = :serviceId
              AND recorded_at >= :since
            """, nativeQuery = true)
    List<Object[]> findPercentiles(
            @Param("serviceId") Long serviceId,
            @Param("since") LocalDateTime since);

    /**
     * Counts the number of metric records for a service in the given time window
     * where {@code error_flag} is {@code TRUE}.
     *
     * <p>Uses the denormalized {@code error_flag} column to avoid expensive
     * {@code IS NOT NULL} predicates on {@code error_message}.
     *
     * @param serviceId the service identifier
     * @param since     start of the time window (inclusive)
     * @return count of error records in the window
     */
    @Query(value = """
            SELECT COUNT(*) FROM metric_records
            WHERE service_id = :serviceId
              AND recorded_at >= :since
              AND error_flag = true
            """, nativeQuery = true)
    long countErrors(
            @Param("serviceId") Long serviceId,
            @Param("since") LocalDateTime since);
}
