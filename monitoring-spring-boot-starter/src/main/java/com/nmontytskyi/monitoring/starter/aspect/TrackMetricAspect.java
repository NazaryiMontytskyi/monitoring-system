package com.nmontytskyi.monitoring.starter.aspect;

import com.nmontytskyi.monitoring.annotation.TrackMetric;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP aspect that intercepts methods annotated with {@link TrackMetric} and records
 * execution timing and success/failure status into the {@link MetricsBuffer}.
 *
 * <p>Metrics are buffered and flushed in batches to the monitoring server,
 * minimising per-call HTTP overhead compared to the individual-push model.
 *
 * <p>All exceptions are re-thrown after recording so business logic is unaffected.
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class TrackMetricAspect {

    private final MetricsBuffer buffer;
    private final ServiceRegistrationBean registrationBean;

    @Around("@annotation(trackMetric)")
    public Object around(ProceedingJoinPoint pjp, TrackMetric trackMetric) throws Throwable {
        Long serviceId = registrationBean.getServiceId();
        long start = System.nanoTime();
        Throwable thrown = null;
        Object result = null;

        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            thrown = t;
        } finally {
            if (serviceId != null) {
                long responseTimeMs = (System.nanoTime() - start) / 1_000_000;
                HealthStatus status = thrown != null ? HealthStatus.DOWN : HealthStatus.UP;

                MetricPushRequest pushRequest = MetricPushRequest.builder()
                        .serviceId(serviceId)
                        .endpoint(trackMetric.name())
                        .responseTimeMs(responseTimeMs)
                        .status(status)
                        .errorMessage(thrown != null ? thrown.getMessage() : null)
                        .build();

                try {
                    buffer.add(pushRequest);
                } catch (Exception e) {
                    log.warn("Failed to buffer @TrackMetric metric '{}': {}", trackMetric.name(), e.getMessage());
                }
            }
        }

        if (thrown != null) {
            throw thrown;
        }
        return result;
    }
}
