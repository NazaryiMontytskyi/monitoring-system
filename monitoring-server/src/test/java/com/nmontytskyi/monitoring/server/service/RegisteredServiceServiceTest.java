package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceAlreadyRegisteredException;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisteredServiceServiceTest {

    @Mock
    private RegisteredServiceRepository repository;

    @InjectMocks
    private RegisteredServiceService service;

    @Test
    void register_success_returnsServiceResponse() {
        ServiceRegistrationRequest req = ServiceRegistrationRequest.builder()
                .name("inventory-service")
                .host("localhost")
                .port(8081)
                .actuatorUrl("http://localhost:8081/actuator")
                .baseUrl("http://localhost:8081")
                .build();

        when(repository.existsByName("inventory-service")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            RegisteredServiceEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        ServiceResponse result = service.register(req);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("inventory-service");
        assertThat(result.getStatus()).isEqualTo(HealthStatus.UNKNOWN);
        verify(repository).save(any(RegisteredServiceEntity.class));
    }

    @Test
    void register_duplicateName_throwsServiceAlreadyRegisteredException() {
        ServiceRegistrationRequest req = ServiceRegistrationRequest.builder()
                .name("duplicate-service")
                .host("localhost")
                .port(8082)
                .actuatorUrl("http://localhost:8082/actuator")
                .build();

        when(repository.existsByName("duplicate-service")).thenReturn(true);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(ServiceAlreadyRegisteredException.class)
                .hasMessageContaining("duplicate-service");

        verify(repository, never()).save(any());
    }

    @Test
    void findById_notFound_throwsServiceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    void findAll_returnsAllServices() {
        RegisteredServiceEntity e1 = buildEntity(1L, "svc-1");
        RegisteredServiceEntity e2 = buildEntity(2L, "svc-2");
        when(repository.findAll()).thenReturn(List.of(e1, e2));

        List<ServiceResponse> result = service.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ServiceResponse::getName)
                .containsExactly("svc-1", "svc-2");
    }

    private RegisteredServiceEntity buildEntity(Long id, String name) {
        return RegisteredServiceEntity.builder()
                .id(id)
                .name(name)
                .host("localhost")
                .port(8080)
                .status(HealthStatus.UNKNOWN)
                .build();
    }
}
