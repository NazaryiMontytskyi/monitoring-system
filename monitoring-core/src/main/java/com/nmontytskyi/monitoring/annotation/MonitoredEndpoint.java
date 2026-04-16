package com.nmontytskyi.monitoring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a REST controller method for individual endpoint monitoring.
 *
 * <p>Used together with {@code @MonitoredMicroservice(trackAllEndpoints = false)}
 * when selective monitoring of specific endpoints is preferred over monitoring all of them.
 * If {@code trackAllEndpoints = true} this annotation is not required,
 * but can still be used to override the name or settings for a specific method.
 *
 * <p>Usage example:
 * <pre>{@code
 * @RestController
 * public class OrderController {
 *
 *     @MonitoredEndpoint
 *     @PostMapping("/orders")
 *     public Order createOrder(@RequestBody OrderRequest request) { ... }
 *
 *     @MonitoredEndpoint(name = "get-all-orders", alertOnSlowResponse = true)
 *     @GetMapping("/orders")
 *     public List<Order> getOrders() { ... }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MonitoredEndpoint {

    /**
     * Logical name of the endpoint in the monitoring system.
     * If not specified, the HTTP method and path are used (e.g. {@code "GET /orders"}).
     */
    String name() default "";

    /**
     * If {@code true}, an additional alert is sent when the response time
     * of this endpoint exceeds the threshold defined in {@code @Sla}.
     */
    boolean alertOnSlowResponse() default false;
}
