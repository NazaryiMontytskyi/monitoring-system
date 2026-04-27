package com.nmontytskyi.monitoring.server.prediction;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.alert.AlertCooldownManager;
import com.nmontytskyi.monitoring.server.alert.AlertNotificationService;
import com.nmontytskyi.monitoring.server.config.PredictionProperties;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.AlertRuleRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictiveAlertService {

    private final MetricRecordRepository metricRecordRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final AlertCooldownManager alertCooldownManager;
    private final AlertNotificationService alertNotificationService;
    private final PredictionProperties predictionProperties;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void runPredictiveChecks() {
        if (!predictionProperties.isEnabled()) {
            return;
        }

        List<AlertRuleEntity> rules = alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue();
        for (AlertRuleEntity rule : rules) {
            try {
                processRule(rule);
            } catch (Exception e) {
                log.warn("Predictive check failed for rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void processRule(AlertRuleEntity rule) {
        Long serviceId = rule.getService().getId();
        int needed = Math.max(rule.getMinDataPoints() * 2, 20);

        List<MetricRecordEntity> records = metricRecordRepository
                .findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(serviceId, MetricSource.PULL);

        if (records.size() < rule.getMinDataPoints()) {
            log.debug("Rule id={}: not enough data points ({}/{})", rule.getId(), records.size(), rule.getMinDataPoints());
            return;
        }

        List<MetricRecordEntity> window = records.subList(0, Math.min(needed, records.size()));

        List<Double> timestamps = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (MetricRecordEntity record : window) {
            Double value = extractMetricValue(rule.getMetricType(), record, window);
            if (value == null) continue;
            timestamps.add((double) record.getRecordedAt().toEpochSecond(
                    java.time.ZoneOffset.UTC));
            values.add(value);
        }

        if (values.size() < rule.getMinDataPoints()) {
            return;
        }

        LinearRegressionEngine.PredictionResult result = LinearRegressionEngine.predict(
                timestamps, values, rule.getLookaheadMinutes() * 60.0);

        if (result.rSquared() < predictionProperties.getMinRSquared()) {
            log.debug("Rule id={}: R²={:.3f} below threshold, skipping", rule.getId(), result.rSquared());
            return;
        }

        boolean conditionMet = switch (rule.getComparator()) {
            case GT -> result.predictedValue() > rule.getThreshold();
            case LT -> result.predictedValue() < rule.getThreshold();
        };

        if (!conditionMet) {
            return;
        }

        if (!alertCooldownManager.isCooldownExpired(rule)) {
            log.debug("Rule id={}: still in cooldown, skipping predictive alert", rule.getId());
            return;
        }

        String message = String.format(
                "PREDICTIVE: %s expected to reach %.1f in %d min (confidence %.0f%%)",
                rule.getMetricType(), result.predictedValue(),
                rule.getLookaheadMinutes(), result.rSquared() * 100);

        AlertEventEntity event = AlertEventEntity.builder()
                .rule(rule)
                .service(rule.getService())
                .firedAt(LocalDateTime.now())
                .metricValue(result.predictedValue())
                .message(message)
                .predictive(true)
                .predictedBreachAt(LocalDateTime.now().plusMinutes(rule.getLookaheadMinutes()))
                .confidenceScore(result.rSquared())
                .notificationSent(false)
                .build();

        alertEventRepository.save(event);

        try {
            alertNotificationService.sendAlert(event, rule, rule.getService());
            event.setNotificationSent(true);
            alertEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to send notification for predictive alert rule id={}: {}", rule.getId(), e.getMessage());
        }

        log.info("Predictive alert fired for rule id={}, service id={}: {}", rule.getId(), serviceId, message);
    }

    private Double extractMetricValue(AlertRuleEntity.MetricType metricType,
                                      MetricRecordEntity record,
                                      List<MetricRecordEntity> window) {
        return switch (metricType) {
            case RESPONSE_TIME_AVG -> (double) record.getResponseTimeMs();
            case CPU_USAGE -> {
                Double cpu = record.getCpuUsage();
                yield (cpu != null) ? cpu * 100.0 : null;
            }
            case UPTIME_PERCENT -> {
                long upCount = window.stream()
                        .filter(r -> r.getStatus() == HealthStatus.UP)
                        .count();
                yield window.isEmpty() ? null : (upCount * 100.0) / window.size();
            }
            case ERROR_RATE -> {
                long errorCount = window.stream()
                        .filter(MetricRecordEntity::isErrorFlag)
                        .count();
                yield window.isEmpty() ? null : (errorCount * 100.0) / window.size();
            }
            case STATUS_DOWN -> record.getStatus() == HealthStatus.DOWN ? 1.0 : 0.0;
        };
    }
}
