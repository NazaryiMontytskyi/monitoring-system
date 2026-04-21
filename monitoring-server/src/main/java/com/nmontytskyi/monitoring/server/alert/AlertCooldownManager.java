package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertCooldownManager {

    private final AlertEventRepository alertEventRepository;

    /**
     * Returns true if the cooldown has expired (i.e., no recent event exists for the rule),
     * meaning a new alert may be fired. Always reads from DB — no in-memory state.
     */
    public boolean isCooldownExpired(AlertRuleEntity rule) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(rule.getCooldownMinutes());
        boolean recentEventExists = alertEventRepository.existsByRuleIdAndFiredAtAfter(rule.getId(), since);
        if (recentEventExists) {
            log.debug("Alert rule {} is in cooldown (last event within {} min)", rule.getId(), rule.getCooldownMinutes());
        }
        return !recentEventExists;
    }
}
