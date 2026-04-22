package com.nmontytskyi.monitoring.annotation;

import com.nmontytskyi.monitoring.model.MetricKind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an arbitrary method for execution time measurement as a technical metric.
 *
 * <p>Unlike {@link MonitoredEndpoint}, this annotation can be applied to any
 * Spring-managed component: a service, repository, HTTP client, etc.
 * It measures the method execution time and records it as a named metric.
 *
 * <p>Usage example:
 * <pre>{@code
 * @Service
 * public class InventoryService {
 *
 *     @TrackMetric(name = "db.inventory.query", kind = MetricKind.TIMER)
 *     public List<Product> findAvailable() { ... }
 *
 *     @TrackMetric(name = "orders.created", kind = MetricKind.COUNTER, description = "Orders placed")
 *     public Order createOrder(OrderRequest request) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackMetric {

    /**
     * Unique metric name in {@code "category.subcategory.action"} format.
     * Examples: {@code "db.query.time"}, {@code "orders.created"}.
     */
    String name();

    /**
     * How the metric value should be interpreted.
     * Defaults to {@link MetricKind#COUNTER}.
     */
    MetricKind kind() default MetricKind.COUNTER;

    /**
     * Unit of measurement. Defaults to milliseconds.
     * Used only for display purposes in the UI.
     */
    String unit() default "ms";

    /**
     * Human-readable description shown in the web interface and Swagger documentation.
     */
    String description() default "";
}
