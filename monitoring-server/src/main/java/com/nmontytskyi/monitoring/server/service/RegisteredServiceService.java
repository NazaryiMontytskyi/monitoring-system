package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceAlreadyRegisteredException;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisteredServiceService {

    private final RegisteredServiceRepository repository;

    @Transactional
    public ServiceResponse register(ServiceRegistrationRequest request) {
        if (repository.existsByName(request.getName())) {
            throw new ServiceAlreadyRegisteredException(request.getName());
        }
        RegisteredServiceEntity entity = RegisteredServiceEntity.builder()
                .name(request.getName())
                .host(request.getHost())
                .port(request.getPort())
                .actuatorUrl(request.getActuatorUrl())
                .baseUrl(request.getBaseUrl())
                .status(HealthStatus.UNKNOWN)
                .lastSeenAt(LocalDateTime.now())
                .build();
        RegisteredServiceEntity saved = repository.save(entity);
        log.info("Registered service '{}' with id={}", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse findById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ServiceNotFoundException(id));
    }

    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new ServiceNotFoundException(id);
        }
        repository.deleteById(id);
        log.info("Deleted service id={}", id);
    }

    private ServiceResponse toResponse(RegisteredServiceEntity e) {
        return ServiceResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .host(e.getHost())
                .port(e.getPort())
                .actuatorUrl(e.getActuatorUrl())
                .baseUrl(e.getBaseUrl())
                .status(e.getStatus())
                .registeredAt(e.getRegisteredAt())
                .lastSeenAt(e.getLastSeenAt())
                .build();
    }
}
