package com.nmontytskyi.monitoring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for business metric collection after each successful invocation.
 *
 * <p>Allows correlating business activity with technical metrics of the service.
 * For example, if CPU rises to 90%, it becomes visible that the number of processed
 * orders dropped simultaneously. The metric is incremented by {@code 1} on each
 * successful method call without an exception.
 *
 * <p>Usage example:
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @TrackBusinessMetric(name = "orders.created", unit = "count")
 *     public Order createOrder(OrderRequest request) { ... }
 *
 *     @TrackBusinessMetric(name = "orders.cancelled", unit = "count", description = "Cancelled orders")
 *     public void cancelOrder(Long orderId) { ... }
 *
 *     @TrackBusinessMetric(name = "payments.processed", unit = "uah", description = "Processed payment amount")
 *     public Payment processPayment(PaymentRequest request) { ... }
 * }
 * }</pre>
 *
 * <p><b>Note:</b> the metric is recorded only on successful method completion.
 * If an exception is thrown, it is not recorded.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackBusinessMetric {

    /**
     * Unique business metric name in {@code "entity.action"} format.
     * Examples: {@code "orders.created"}, {@code "payments.failed"}.
     */
    String name();

    /**
     * Unit of measurement. Examples: {@code "count"}, {@code "uah"}, {@code "items"}.
     */
    String unit() default "count";

    /**
     * Human-readable description shown in reports and the dashboard.
     */
    String description() default "";
}
