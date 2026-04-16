package com.nmontytskyi.monitoring.server.dto.request;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleRequest {

    @NotNull
    private Long serviceId;

    @NotNull
    private AlertRuleEntity.MetricType metricType;

    @NotNull
    private AlertRuleEntity.Comparator comparator;

    @NotNull
    private Double threshold;

    private boolean enabled;

    private int cooldownMinutes;
}
