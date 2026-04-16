package com.nmontytskyi.monitoring.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRegistrationRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String host;

    @Positive
    private int port;

    @NotBlank
    private String actuatorUrl;

    private String baseUrl;
}
