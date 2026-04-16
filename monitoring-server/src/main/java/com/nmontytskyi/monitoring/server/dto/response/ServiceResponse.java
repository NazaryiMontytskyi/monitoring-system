package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {

    private Long id;
    private String name;
    private String host;
    private int port;
    private String actuatorUrl;
    private String baseUrl;
    private HealthStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
}
