package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.dto.request.AlertRuleRequest;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.AlertRuleRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final RegisteredServiceRepository serviceRepository;

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest request) {
        RegisteredServiceEntity service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.getServiceId()));

        AlertRuleEntity entity = AlertRuleEntity.builder()
                .service(service)
                .metricType(request.getMetricType())
                .comparator(request.getComparator())
                .threshold(request.getThreshold())
                .enabled(request.isEnabled())
                .cooldownMinutes(request.getCooldownMinutes() > 0 ? request.getCooldownMinutes() : 15)
                .predictiveEnabled(request.isPredictiveEnabled())
                .lookaheadMinutes(request.getLookaheadMinutes() > 0 ? request.getLookaheadMinutes() : 10)
                .minDataPoints(request.getMinDataPoints() > 0 ? request.getMinDataPoints() : 5)
                .build();

        AlertRuleEntity saved = alertRuleRepository.save(entity);
        log.info("Created alert rule id={} for service id={}", saved.getId(), request.getServiceId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> findByServiceId(Long serviceId) {
        if (serviceId == null) {
            return alertRuleRepository.findAll().stream().map(this::toResponse).toList();
        }
        return alertRuleRepository.findAllByServiceId(serviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteById(Long id) {
        alertRuleRepository.deleteById(id);
        log.info("Deleted alert rule id={}", id);
    }

    private AlertRuleResponse toResponse(AlertRuleEntity e) {
        return AlertRuleResponse.builder()
                .id(e.getId())
                .serviceId(e.getService().getId())
                .metricType(e.getMetricType())
                .comparator(e.getComparator())
                .threshold(e.getThreshold())
                .enabled(e.isEnabled())
                .cooldownMinutes(e.getCooldownMinutes())
                .createdAt(e.getCreatedAt())
                .predictiveEnabled(e.isPredictiveEnabled())
                .lookaheadMinutes(e.getLookaheadMinutes())
                .minDataPoints(e.getMinDataPoints())
                .build();
    }
}
