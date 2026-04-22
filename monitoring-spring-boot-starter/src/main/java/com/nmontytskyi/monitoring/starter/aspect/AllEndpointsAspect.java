package com.nmontytskyi.monitoring.starter.aspect;

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
 * AOP aspect that intercepts <em>all</em> public methods of {@code @RestController} classes
 * when {@code monitoring.track-all-endpoints=true} (or {@code trackAllEndpoints=true} in
 * {@code @MonitoredMicroservice}).
 *
 * <p>Methods already annotated with {@code @MonitoredEndpoint} are excluded to prevent
 * double-recording — {@link MonitoredEndpointAspect} handles those individually.
 *
 * <p>Metrics are buffered via {@link MetricsBuffer} and flushed in batches.
 * All exceptions propagate normally; monitoring failures are logged at WARN level.
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class AllEndpointsAspect {

    private final MetricsBuffer buffer;
    private final ServiceRegistrationBean registrationBean;

    @Around("within(@org.springframework.web.bind.annotation.RestController *) " +
            "&& !@annotation(com.nmontytskyi.monitoring.annotation.MonitoredEndpoint)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
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
                String endpoint = pjp.getTarget().getClass().getSimpleName()
                        + "." + pjp.getSignature().getName();

                MetricPushRequest pushRequest = MetricPushRequest.builder()
                        .serviceId(serviceId)
                        .endpoint(endpoint)
                        .responseTimeMs(responseTimeMs)
                        .status(status)
                        .errorMessage(thrown != null ? thrown.getMessage() : null)
                        .build();

                try {
                    buffer.add(pushRequest);
                } catch (Exception e) {
                    log.warn("Failed to buffer metric for endpoint '{}': {}", endpoint, e.getMessage());
                }
            }
        }

        if (thrown != null) {
            throw thrown;
        }
        return result;
    }
}
