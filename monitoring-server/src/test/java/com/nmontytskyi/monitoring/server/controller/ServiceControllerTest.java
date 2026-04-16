package com.nmontytskyi.monitoring.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.exception.ServiceAlreadyRegisteredException;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceController.class)
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisteredServiceService service;

    @Test
    void post_register_returns201WithLocation() throws Exception {
        ServiceRegistrationRequest req = ServiceRegistrationRequest.builder()
                .name("inventory-service")
                .host("localhost")
                .port(8081)
                .actuatorUrl("http://localhost:8081/actuator")
                .build();

        when(service.register(any())).thenReturn(buildServiceResponse(1L, "inventory-service"));

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/services/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("inventory-service"));
    }

    @Test
    void post_register_duplicate_returns409() throws Exception {
        ServiceRegistrationRequest req = ServiceRegistrationRequest.builder()
                .name("duplicate")
                .host("localhost")
                .port(8081)
                .actuatorUrl("http://localhost:8081/actuator")
                .build();

        when(service.register(any())).thenThrow(new ServiceAlreadyRegisteredException("duplicate"));

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void get_findAll_returns200WithList() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                buildServiceResponse(1L, "svc-1"),
                buildServiceResponse(2L, "svc-2")
        ));

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void get_findById_existing_returns200() throws Exception {
        when(service.findById(1L)).thenReturn(buildServiceResponse(1L, "inventory-service"));

        mockMvc.perform(get("/api/services/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void get_findById_notFound_returns404() throws Exception {
        when(service.findById(99L)).thenThrow(new ServiceNotFoundException(99L));

        mockMvc.perform(get("/api/services/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(service).deleteById(1L);

        mockMvc.perform(delete("/api/services/1"))
                .andExpect(status().isNoContent());
    }

    private ServiceResponse buildServiceResponse(Long id, String name) {
        return ServiceResponse.builder()
                .id(id)
                .name(name)
                .host("localhost")
                .port(8081)
                .actuatorUrl("http://localhost:8081/actuator")
                .status(HealthStatus.UNKNOWN)
                .registeredAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();
    }
}
