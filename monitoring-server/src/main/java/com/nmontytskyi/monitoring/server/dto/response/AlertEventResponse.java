package com.nmontytskyi.monitoring.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventResponse {

    private Long id;
    private Long ruleId;
    private Long serviceId;
    private LocalDateTime firedAt;
    private double metricValue;
    private String message;
    private boolean notificationSent;
}
