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
