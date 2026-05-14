package com.nmontytskyi.monitoring.starter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned by the monitoring server after successful service registration.
 *
 * <p>Contains the server-assigned {@code serviceId} which the starter uses in all
 * subsequent metric push requests to identify the originating service.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceRegistrationResponse {

    private Long id;
}
