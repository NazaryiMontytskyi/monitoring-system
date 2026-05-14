package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * In-memory cooldown tracker that prevents the same alert rule from firing repeatedly
 * within its configured cooldown period.
 *
 * <p>For each rule the manager records the timestamp of the last firing. Before
 * {@link com.nmontytskyi.monitoring.server.alert.AlertEvaluationService} creates a new
 * {@link com.nmontytskyi.monitoring.server.entity.AlertEventEntity} it consults this
 * component to verify the cooldown has elapsed. State is held in a
 * {@link java.util.concurrent.ConcurrentHashMap} and is therefore not persisted across
 * server restarts — after a restart all rules fire on their next breach.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.alert.AlertEvaluationService
 */
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
