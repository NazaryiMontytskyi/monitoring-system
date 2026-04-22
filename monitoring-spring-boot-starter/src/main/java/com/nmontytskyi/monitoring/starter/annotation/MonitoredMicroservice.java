package com.nmontytskyi.monitoring.starter.annotation;

import com.nmontytskyi.monitoring.annotation.Sla;
import com.nmontytskyi.monitoring.starter.config.MonitoringAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables full monitoring for a Spring Boot microservice with a single annotation.
 *
 * <p>Place this annotation on the main {@code @SpringBootApplication} class.
 * The monitoring starter will auto-register the service, intercept endpoints,
 * buffer metrics, and flush them in batches to the monitoring server.
 *
 * <p>All annotation attributes act as <em>defaults</em>: they are applied first
 * and can be overridden via {@code application.yml} (e.g. {@code monitoring.server-url}).
 *
 * <p>Usage example:
 * <pre>{@code
 * @SpringBootApplication
 * @MonitoredMicroservice(
 *     name = "order-service",
 *     serverUrl = "http://monitoring-server:8080",
 *     sla = @Sla(maxResponseTimeMs = 500, uptimePercent = 99.9),
 *     trackAllEndpoints = true
 * )
 * public class OrderServiceApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(OrderServiceApplication.class, args);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MonitoringAutoConfiguration.class)
public @interface MonitoredMicroservice {

    /** Logical service name registered in the monitoring server. */
    String name();

    /** Base URL of the monitoring server. Overridable via {@code monitoring.server-url}. */
    String serverUrl() default "http://localhost:8080";

    /** SLA parameters for this service. */
    Sla sla() default @Sla;

    /**
     * When {@code true}, all {@code @RestController} methods are intercepted automatically
     * without requiring {@code @MonitoredEndpoint} on each method.
     * Overridable via {@code monitoring.track-all-endpoints}.
     */
    boolean trackAllEndpoints() default false;

    /** How often buffered metrics are flushed to the server, in milliseconds. */
    long bufferFlushIntervalMs() default 5000;

    /** Maximum number of metrics to buffer before triggering an immediate flush. */
    int bufferMaxSize() default 100;
}
