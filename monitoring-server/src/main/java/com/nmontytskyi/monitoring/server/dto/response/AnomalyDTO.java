package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a single anomaly record shown in the anomaly list modal.
 * Includes the service identity, timestamp, health status, response time, and
 * z-score. The z-score field is {@code null} when the value is non-finite
 * (e.g. when standard deviation is zero and a sentinel was used internally).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDTO {
    private Long serviceId;
    private String serviceName;
    private LocalDateTime recordedAt;
    private Double zScore;
    private double responseTimeMs;
    private HealthStatus status;
}
