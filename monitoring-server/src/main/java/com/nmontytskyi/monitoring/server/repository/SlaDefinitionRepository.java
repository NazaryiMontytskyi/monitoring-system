package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.server.entity.SlaDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SlaDefinitionRepository extends JpaRepository<SlaDefinitionEntity, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO sla_definitions
                (service_id, uptime_percent, max_response_time_ms, max_error_rate_percent, description)
            VALUES
                (:serviceId, :uptimePercent, :maxResponseTimeMs, :maxErrorRatePercent, :description)
            ON CONFLICT (service_id) DO UPDATE SET
                uptime_percent         = EXCLUDED.uptime_percent,
                max_response_time_ms   = EXCLUDED.max_response_time_ms,
                max_error_rate_percent = EXCLUDED.max_error_rate_percent,
                description            = EXCLUDED.description
            """, nativeQuery = true)
    void upsert(@Param("serviceId") Long serviceId,
                @Param("uptimePercent") double uptimePercent,
                @Param("maxResponseTimeMs") long maxResponseTimeMs,
                @Param("maxErrorRatePercent") double maxErrorRatePercent,
                @Param("description") String description);
}
