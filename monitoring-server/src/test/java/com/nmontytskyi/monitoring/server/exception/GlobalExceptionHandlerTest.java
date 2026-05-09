package com.nmontytskyi.monitoring.server.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nmontytskyi.monitoring.server.controller.ServiceController;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ServiceController.class)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RegisteredServiceService registeredServiceService;

    @Test
    void serviceNotFoundException_returns404WithErrorBody() throws Exception {
        when(registeredServiceService.findById(anyLong()))
                .thenThrow(new ServiceNotFoundException(42L));

        mockMvc.perform(get("/api/services/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Service not found with id: 42"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void serviceAlreadyRegisteredException_returns409WithErrorBody() throws Exception {
        when(registeredServiceService.register(any()))
                .thenThrow(new ServiceAlreadyRegisteredException("payment-service"));

        String body = """
                {
                  "name": "payment-service",
                  "host": "localhost",
                  "port": 8083,
                  "actuatorUrl": "http://localhost:8083/actuator",
                  "baseUrl": "http://localhost:8083"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Service already registered with name: payment-service"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void validationException_missingRequiredField_returns400() throws Exception {
        String bodyMissingName = """
                {
                  "host": "localhost",
                  "port": 8083
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
