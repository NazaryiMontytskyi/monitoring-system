package com.nmontytskyi.monitoring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the Service Level Agreement (SLA) for a microservice.
 *
 * <p>Used exclusively as an attribute of the {@code @MonitoredMicroservice} annotation.
 * The monitoring system automatically calculates SLA compliance over different
 * time windows (hour, day, week, month) and records breaches.
 *
 * <p>Usage example:
 * <pre>{@code
 * @MonitoredMicroservice(
 *     name = "order-service",
 *     sla = @Sla(
 *         uptimePercent       = 99.9,
 *         maxResponseTimeMs   = 300,
 *         maxErrorRatePercent = 1.0,
 *         description         = "Production SLA for order processing"
 *     )
 * )
 * @SpringBootApplication
 * public class OrderServiceApplication { ... }
 * }</pre>
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sla {

    /**
     * Minimum required uptime percentage.
     * Defaults to 99.9% (three nines).
     */
    double uptimePercent() default 99.9;

    /**
     * Maximum allowed average response time in milliseconds.
     * Defaults to 1000ms.
     */
    long maxResponseTimeMs() default 1000;

    /**
     * Maximum allowed error rate as a percentage of total requests.
     * Defaults to 5.0%.
     */
    double maxErrorRatePercent() default 5.0;

    /**
     * Human-readable description of the agreement shown in reports.
     */
    String description() default "";
}
