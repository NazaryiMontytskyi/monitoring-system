package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.dto.response.AlertEventResponse;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEventServiceTest {

    @Mock private AlertEventRepository alertEventRepository;

    @InjectMocks private AlertEventService alertEventService;

    private RegisteredServiceEntity buildService(Long id) {
        return RegisteredServiceEntity.builder().id(id).name("svc-" + id).build();
    }

    private AlertRuleEntity buildRule(Long id, RegisteredServiceEntity svc) {
        return AlertRuleEntity.builder()
                .id(id)
                .service(svc)
                .metricType(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(500.0)
                .build();
    }

    private AlertEventEntity buildEvent(Long id, RegisteredServiceEntity svc, AlertRuleEntity rule, boolean predictive) {
        return AlertEventEntity.builder()
                .id(id)
                .service(svc)
                .rule(rule)
                .firedAt(LocalDateTime.of(2026, 1, 10, 12, 0))
                .metricValue(750.0)
                .message("test alert")
                .notificationSent(true)
                .predictive(predictive)
                .predictedBreachAt(predictive ? LocalDateTime.of(2026, 1, 10, 12, 10) : null)
                .confidenceScore(predictive ? 0.85 : null)
                .build();
    }

    @Test
    void findByServiceId_returnsMappedPage() {
        RegisteredServiceEntity svc = buildService(1L);
        AlertRuleEntity rule = buildRule(10L, svc);
        AlertEventEntity event = buildEvent(100L, svc, rule, false);

        Pageable pageable = PageRequest.of(0, 10);
        when(alertEventRepository.findAllByServiceIdOrderByFiredAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AlertEventResponse> result = alertEventService.findByServiceId(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        AlertEventResponse resp = result.getContent().get(0);
        assertThat(resp.getId()).isEqualTo(100L);
        assertThat(resp.getRuleId()).isEqualTo(10L);
        assertThat(resp.getServiceId()).isEqualTo(1L);
        assertThat(resp.getMetricValue()).isEqualTo(750.0);
        assertThat(resp.getMessage()).isEqualTo("test alert");
        assertThat(resp.isNotificationSent()).isTrue();
        assertThat(resp.isPredictive()).isFalse();
        assertThat(resp.getPredictedBreachAt()).isNull();
        assertThat(resp.getConfidenceScore()).isNull();
        verify(alertEventRepository).findAllByServiceIdOrderByFiredAtDesc(1L, pageable);
    }

    @Test
    void findByServiceId_predictiveEvent_mapsPredictiveFields() {
        RegisteredServiceEntity svc = buildService(2L);
        AlertRuleEntity rule = buildRule(20L, svc);
        AlertEventEntity event = buildEvent(200L, svc, rule, true);

        Pageable pageable = PageRequest.of(0, 5);
        when(alertEventRepository.findAllByServiceIdOrderByFiredAtDesc(2L, pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AlertEventResponse> result = alertEventService.findByServiceId(2L, pageable);

        AlertEventResponse resp = result.getContent().get(0);
        assertThat(resp.isPredictive()).isTrue();
        assertThat(resp.getPredictedBreachAt()).isEqualTo(LocalDateTime.of(2026, 1, 10, 12, 10));
        assertThat(resp.getConfidenceScore()).isEqualTo(0.85);
    }

    @Test
    void findByServiceId_emptyPage_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(alertEventRepository.findAllByServiceIdOrderByFiredAtDesc(99L, pageable))
                .thenReturn(Page.empty(pageable));

        Page<AlertEventResponse> result = alertEventService.findByServiceId(99L, pageable);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void findByServiceId_multipleEvents_allMapped() {
        RegisteredServiceEntity svc = buildService(3L);
        AlertRuleEntity rule = buildRule(30L, svc);
        AlertEventEntity e1 = buildEvent(301L, svc, rule, false);
        AlertEventEntity e2 = buildEvent(302L, svc, rule, true);

        Pageable pageable = PageRequest.of(0, 10);
        when(alertEventRepository.findAllByServiceIdOrderByFiredAtDesc(3L, pageable))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));

        Page<AlertEventResponse> result = alertEventService.findByServiceId(3L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(AlertEventResponse::getId)
                .containsExactly(301L, 302L);
    }
}
