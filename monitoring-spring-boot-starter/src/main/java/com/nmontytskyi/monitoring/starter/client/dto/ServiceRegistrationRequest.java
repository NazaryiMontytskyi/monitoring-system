package com.nmontytskyi.monitoring.starter.client.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceRegistrationRequest {

    private String name;
    private String host;
    private int port;
    private String actuatorUrl;
    private String baseUrl;
}
