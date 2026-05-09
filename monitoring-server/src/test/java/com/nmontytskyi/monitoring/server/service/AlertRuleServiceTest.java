package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.dto.request.AlertRuleRequest;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.AlertRuleRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private RegisteredServiceRepository serviceRepository;

    @InjectMocks private AlertRuleService alertRuleService;

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
                .enabled(true)
                .cooldownMinutes(15)
                .predictiveEnabled(false)
                .lookaheadMinutes(10)
                .minDataPoints(5)
                .build();
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_success_returnsResponse() {
        RegisteredServiceEntity svc = buildService(1L);
        AlertRuleRequest req = AlertRuleRequest.builder()
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(1000.0)
                .enabled(true)
                .cooldownMinutes(20)
                .predictiveEnabled(false)
                .lookaheadMinutes(10)
                .minDataPoints(5)
                .build();

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(svc));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRuleEntity e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });

        AlertRuleResponse resp = alertRuleService.create(req);

        assertThat(resp.getId()).isEqualTo(42L);
        assertThat(resp.getServiceId()).isEqualTo(1L);
        assertThat(resp.getMetricType()).isEqualTo(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG);
        assertThat(resp.getComparator()).isEqualTo(AlertRuleEntity.Comparator.GT);
        assertThat(resp.getThreshold()).isEqualTo(1000.0);
        assertThat(resp.getCooldownMinutes()).isEqualTo(20);
        verify(alertRuleRepository).save(any(AlertRuleEntity.class));
    }

    @Test
    void create_serviceNotFound_throwsServiceNotFoundException() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        AlertRuleRequest req = AlertRuleRequest.builder()
                .serviceId(99L)
                .metricType(AlertRuleEntity.MetricType.CPU_USAGE)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(90.0)
                .build();

        assertThatThrownBy(() -> alertRuleService.create(req))
                .isInstanceOf(ServiceNotFoundException.class)
                .hasMessageContaining("99");

        verify(alertRuleRepository, never()).save(any());
    }

    @Test
    void create_zeroCooldown_usesDefaultFifteen() {
        RegisteredServiceEntity svc = buildService(1L);
        AlertRuleRequest req = AlertRuleRequest.builder()
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.STATUS_DOWN)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(0.5)
                .cooldownMinutes(0)
                .build();

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(svc));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRuleEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        AlertRuleResponse resp = alertRuleService.create(req);

        assertThat(resp.getCooldownMinutes()).isEqualTo(15);
    }

    @Test
    void create_zeroLookahead_usesDefaultTen() {
        RegisteredServiceEntity svc = buildService(1L);
        AlertRuleRequest req = AlertRuleRequest.builder()
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.CPU_USAGE)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(80.0)
                .lookaheadMinutes(0)
                .minDataPoints(0)
                .build();

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(svc));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRuleEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        AlertRuleResponse resp = alertRuleService.create(req);

        assertThat(resp.getLookaheadMinutes()).isEqualTo(10);
        assertThat(resp.getMinDataPoints()).isEqualTo(5);
    }

    @Test
    void create_predictiveEnabled_persisted() {
        RegisteredServiceEntity svc = buildService(1L);
        AlertRuleRequest req = AlertRuleRequest.builder()
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(500.0)
                .predictiveEnabled(true)
                .lookaheadMinutes(15)
                .minDataPoints(8)
                .build();

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(svc));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRuleEntity e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });

        AlertRuleResponse resp = alertRuleService.create(req);

        assertThat(resp.isPredictiveEnabled()).isTrue();
        assertThat(resp.getLookaheadMinutes()).isEqualTo(15);
        assertThat(resp.getMinDataPoints()).isEqualTo(8);
    }

    // ── findByServiceId ──────────────────────────────────────────────────────

    @Test
    void findByServiceId_nullId_returnsAll() {
        RegisteredServiceEntity svc = buildService(1L);
        List<AlertRuleEntity> all = List.of(buildRule(1L, svc), buildRule(2L, svc));
        when(alertRuleRepository.findAll()).thenReturn(all);

        List<AlertRuleResponse> result = alertRuleService.findByServiceId(null);

        assertThat(result).hasSize(2);
        verify(alertRuleRepository).findAll();
        verify(alertRuleRepository, never()).findAllByServiceId(any());
    }

    @Test
    void findByServiceId_specificId_filtersCorrectly() {
        RegisteredServiceEntity svc = buildService(3L);
        when(alertRuleRepository.findAllByServiceId(3L)).thenReturn(List.of(buildRule(7L, svc)));

        List<AlertRuleResponse> result = alertRuleService.findByServiceId(3L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceId()).isEqualTo(3L);
        verify(alertRuleRepository, never()).findAll();
    }

    @Test
    void findByServiceId_noRulesForService_returnsEmptyList() {
        when(alertRuleRepository.findAllByServiceId(99L)).thenReturn(List.of());

        List<AlertRuleResponse> result = alertRuleService.findByServiceId(99L);

        assertThat(result).isEmpty();
    }

    // ── deleteById ───────────────────────────────────────────────────────────

    @Test
    void deleteById_delegatesToRepository() {
        doNothing().when(alertRuleRepository).deleteById(5L);

        alertRuleService.deleteById(5L);

        verify(alertRuleRepository).deleteById(5L);
    }
}
