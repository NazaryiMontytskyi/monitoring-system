package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.AlertProperties;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.Comparator;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.MetricType;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.AlertRuleRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final AlertProperties alertProperties;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final MetricRecordRepository metricRecordRepository;
    private final AlertCooldownManager alertCooldownManager;
    private final AlertNotificationService alertNotificationService;

    @Transactional
    public void evaluate(Long serviceId, MetricRecordEntity savedRecord) {
        if (!alertProperties.isEnabled()) {
            return;
        }

        List<AlertRuleEntity> rules = alertRuleRepository.findAllByServiceIdAndEnabledTrue(serviceId);
        log.info("Evaluating {} alert rules for service {}", rules.size(), savedRecord.getService().getName());

        for (AlertRuleEntity rule : rules) {
            evaluateRule(rule, savedRecord);
        }
    }

    private void evaluateRule(AlertRuleEntity rule, MetricRecordEntity record) {
        double metricValue;

        switch (rule.getMetricType()) {
            case STATUS_DOWN -> metricValue = record.getStatus() == HealthStatus.DOWN ? 1.0 : 0.0;

            case RESPONSE_TIME_AVG -> {
                LocalDateTime since = LocalDateTime.now().minusMinutes(alertProperties.getEvaluationWindowMinutes());
                Double avg = metricRecordRepository.avgResponseTimeSince(record.getService().getId(), since);
                metricValue = avg != null ? avg : 0.0;
            }

            case CPU_USAGE -> {
                if (record.getCpuUsage() == null) {
                    log.debug("Skipping CPU_USAGE rule {} — no CPU data in record", rule.getId());
                    return;
                }
                metricValue = record.getCpuUsage();
            }

            case UPTIME_PERCENT -> {
                LocalDateTime since = LocalDateTime.now().minusMinutes(alertProperties.getEvaluationWindowMinutes());
                long total = metricRecordRepository.countByServiceIdSince(record.getService().getId(), since);
                if (total == 0) return;
                long upCount = metricRecordRepository.countByServiceIdAndStatusSince(
                        record.getService().getId(), since, HealthStatus.UP);
                metricValue = (upCount * 100.0) / total;
            }

            case ERROR_RATE -> {
                LocalDateTime since = LocalDateTime.now().minusMinutes(alertProperties.getEvaluationWindowMinutes());
                long total = metricRecordRepository.countByServiceIdSince(record.getService().getId(), since);
                if (total == 0) return;
                long downCount = metricRecordRepository.countByServiceIdAndStatusSince(
                        record.getService().getId(), since, HealthStatus.DOWN);
                metricValue = (downCount * 100.0) / total;
            }

            default -> {
                log.warn("Unknown MetricType {} in rule {}", rule.getMetricType(), rule.getId());
                return;
            }
        }

        boolean violated = rule.getComparator() == Comparator.GT
                ? metricValue > rule.getThreshold()
                : metricValue < rule.getThreshold();

        if (!violated) {
            return;
        }

        if (!alertCooldownManager.isCooldownExpired(rule)) {
            log.debug("Rule {} violated but still in cooldown; suppressing alert", rule.getId());
            return;
        }

        fireAlert(rule, record, metricValue);
    }

    private void fireAlert(AlertRuleEntity rule, MetricRecordEntity record, double metricValue) {
        String message = String.format("[%s] Service '%s': value=%.2f %s threshold=%.2f",
                rule.getMetricType(),
                record.getService().getName(),
                metricValue,
                rule.getComparator(),
                rule.getThreshold());

        AlertEventEntity event = AlertEventEntity.builder()
                .rule(rule)
                .service(record.getService())
                .firedAt(LocalDateTime.now())
                .metricValue(metricValue)
                .message(message)
                .notificationSent(false)
                .build();

        alertNotificationService.sendAlert(event, rule, record.getService());
        event.setNotificationSent(true);

        alertEventRepository.save(event);
        log.warn("Alert fired for service {}: {} = {} {} {}",
                record.getService().getName(),
                rule.getMetricType(),
                metricValue,
                rule.getComparator(),
                rule.getThreshold());
    }
}
