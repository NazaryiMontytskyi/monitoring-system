package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.stereotype.Repository
public interface MetricTimeSeriesRepository extends Repository<MetricRecordEntity, Long> {

    @Query("""
            SELECT m FROM MetricRecordEntity m
            WHERE m.service.id = :serviceId
              AND m.recordedAt >= :from
              AND m.source = :source
            ORDER BY m.recordedAt ASC
            """)
    List<MetricRecordEntity> findRecentByService(
            @Param("serviceId") Long serviceId,
            @Param("from") LocalDateTime from,
            @Param("source") MetricRecordEntity.MetricSource source,
            Pageable pageable);

    @Query(value = """
            SELECT
                TO_TIMESTAMP(FLOOR(EXTRACT(EPOCH FROM recorded_at) / 30) * 30)::timestamp AS bucket,
                AVG(response_time_ms)                      AS avg_rt,
                MAX(response_time_ms)                      AS max_rt,
                AVG(cpu_usage)                             AS avg_cpu,
                AVG(heap_used_mb)                          AS avg_heap,
                COUNT(DISTINCT CASE WHEN status = 'UP'       THEN service_id END) AS up_cnt,
                COUNT(DISTINCT CASE WHEN status = 'DOWN'     THEN service_id END) AS down_cnt,
                COUNT(DISTINCT CASE WHEN status = 'DEGRADED' THEN service_id END) AS deg_cnt,
                COUNT(DISTINCT CASE WHEN anomaly = true      THEN service_id END) AS anomaly_cnt
            FROM metric_records
            WHERE recorded_at >= :from
            GROUP BY FLOOR(EXTRACT(EPOCH FROM recorded_at) / 30)
            ORDER BY bucket ASC
            LIMIT :lim
            """, nativeQuery = true)
    List<Object[]> findSystemAggregated(
            @Param("from") LocalDateTime from,
            @Param("lim") int lim);
}
