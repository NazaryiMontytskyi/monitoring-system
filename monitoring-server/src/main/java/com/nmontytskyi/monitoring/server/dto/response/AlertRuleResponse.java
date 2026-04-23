package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleResponse {

    private Long id;
    private Long serviceId;
    private AlertRuleEntity.MetricType metricType;
    private AlertRuleEntity.Comparator comparator;
    private double threshold;
    private boolean enabled;
    private int cooldownMinutes;
    private LocalDateTime createdAt;
    private boolean predictiveEnabled;
    private int lookaheadMinutes;
    private int minDataPoints;
}
