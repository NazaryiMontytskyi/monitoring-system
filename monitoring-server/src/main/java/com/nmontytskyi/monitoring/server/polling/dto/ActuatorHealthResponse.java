package com.nmontytskyi.monitoring.server.polling.dto;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActuatorHealthResponse {

    private String status;

    public HealthStatus toHealthStatus() {
        if ("UP".equals(status)) return HealthStatus.UP;
        if ("DOWN".equals(status) || "OUT_OF_SERVICE".equals(status)) return HealthStatus.DOWN;
        return HealthStatus.DEGRADED;
    }
}
