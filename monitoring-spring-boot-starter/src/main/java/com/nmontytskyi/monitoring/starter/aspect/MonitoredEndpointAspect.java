package com.nmontytskyi.monitoring.starter.aspect;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP aspect that intercepts methods annotated with {@link com.nmontytskyi.monitoring.annotation.MonitoredEndpoint}
 * and records a {@link com.nmontytskyi.monitoring.model.MetricSnapshot} for each invocation.
 *
 * <p>For every matched method call the aspect measures wall-clock response time, captures
 * the resulting {@link com.nmontytskyi.monitoring.model.HealthStatus} (UP on success, DOWN on
 * exception), and hands the snapshot to the shared {@link com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer}
 * for asynchronous delivery to the monitoring server.
 *
 * <p>This aspect is registered only when {@code monitoring.enabled=true} (the default).
 * It complements {@link com.nmontytskyi.monitoring.starter.aspect.AllEndpointsAspect}, which
 * covers all {@code @RestController} methods when {@code monitoring.track-all-endpoints=true}.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.annotation.MonitoredEndpoint
 * @see com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class MonitoredEndpointAspect {

    private final ServiceRegistrationBean registrationBean;
    private final MonitoringServerClient client;

    @Around("@annotation(com.nmontytskyi.monitoring.annotation.MonitoredEndpoint)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Long serviceId = registrationBean.getServiceId();
        long startTime = System.currentTimeMillis();
        Throwable thrown = null;
        Object result = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            thrown = t;
        } finally {
            if (serviceId != null) {
                long responseTimeMs = System.currentTimeMillis() - startTime;
                HealthStatus status = thrown != null ? HealthStatus.DOWN : HealthStatus.UP;
                String endpoint = joinPoint.getTarget().getClass().getSimpleName()
                        + "." + joinPoint.getSignature().getName();

                MetricPushRequest pushRequest = MetricPushRequest.builder()
                        .serviceId(serviceId)
                        .endpoint(endpoint)
                        .responseTimeMs(responseTimeMs)
                        .status(status)
                        .errorMessage(thrown != null ? thrown.getMessage() : null)
                        .build();

                try {
                    client.pushMetric(pushRequest);
                } catch (Exception e) {
                    log.warn("Failed to push metric in aspect: {}", e.getMessage());
                }
            }
        }

        if (thrown != null) {
            throw thrown;
        }
        return result;
    }
}
