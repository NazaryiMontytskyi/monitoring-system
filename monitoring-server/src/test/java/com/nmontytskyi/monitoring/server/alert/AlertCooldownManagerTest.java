package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertCooldownManagerTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @InjectMocks
    private AlertCooldownManager cooldownManager;

    @Test
    void isCooldownExpired_noRecentEvent_returnsTrue() {
        AlertRuleEntity rule = ruleWithCooldown(15);
        when(alertEventRepository.existsByRuleIdAndFiredAtAfter(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        assertThat(cooldownManager.isCooldownExpired(rule)).isTrue();
    }

    @Test
    void isCooldownExpired_recentEventExists_returnsFalse() {
        AlertRuleEntity rule = ruleWithCooldown(15);
        when(alertEventRepository.existsByRuleIdAndFiredAtAfter(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        assertThat(cooldownManager.isCooldownExpired(rule)).isFalse();
    }

    @Test
    void isCooldownExpired_usesCorrectTimeWindow() {
        AlertRuleEntity rule = ruleWithCooldown(30);
        when(alertEventRepository.existsByRuleIdAndFiredAtAfter(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        LocalDateTime before = LocalDateTime.now().minusMinutes(30);
        cooldownManager.isCooldownExpired(rule);
        LocalDateTime after = LocalDateTime.now().minusMinutes(30);

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertEventRepository).existsByRuleIdAndFiredAtAfter(eq(1L), sinceCaptor.capture());

        LocalDateTime capturedSince = sinceCaptor.getValue();
        assertThat(capturedSince).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    private AlertRuleEntity ruleWithCooldown(int cooldownMinutes) {
        return AlertRuleEntity.builder()
                .id(1L)
                .cooldownMinutes(cooldownMinutes)
                .build();
    }
}
