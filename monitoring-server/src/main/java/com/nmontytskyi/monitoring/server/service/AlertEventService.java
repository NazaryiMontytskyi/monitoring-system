package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.dto.response.AlertEventResponse;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventService {

    private final AlertEventRepository alertEventRepository;

    @Transactional(readOnly = true)
    public Page<AlertEventResponse> findByServiceId(Long serviceId, Pageable pageable) {
        return alertEventRepository.findAllByServiceIdOrderByFiredAtDesc(serviceId, pageable)
                .map(this::toResponse);
    }

    private AlertEventResponse toResponse(AlertEventEntity e) {
        return AlertEventResponse.builder()
                .id(e.getId())
                .ruleId(e.getRule().getId())
                .serviceId(e.getService().getId())
                .firedAt(e.getFiredAt())
                .metricValue(e.getMetricValue())
                .message(e.getMessage())
                .notificationSent(e.isNotificationSent())
                .build();
    }
}
