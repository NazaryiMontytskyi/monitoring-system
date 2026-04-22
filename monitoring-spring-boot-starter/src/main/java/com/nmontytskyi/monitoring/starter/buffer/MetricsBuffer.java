package com.nmontytskyi.monitoring.starter.buffer;

import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.config.MonitoringProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe metric buffer that accumulates {@link MetricPushRequest} records and
 * sends them as a single HTTP batch to the monitoring server.
 *
 * <p><b>Flushing policy:</b>
 * <ul>
 *   <li>Periodic: a scheduler calls {@link #flush()} every {@code bufferFlushIntervalMs} ms.</li>
 *   <li>Immediate: if the queue reaches {@code bufferMaxSize}, {@link #flush()} is triggered
 *       synchronously inside the calling thread.</li>
 *   <li>Shutdown: {@link #shutdown()} (annotated with {@code @PreDestroy}) flushes any
 *       remaining metrics before the JVM stops.</li>
 * </ul>
 *
 * <p><b>Fail-safe behaviour:</b> if the HTTP batch call fails, the drained metrics are
 * returned to the queue so they are retried on the next flush. This prevents metric loss
 * but may cause memory growth when the monitoring server is permanently unavailable.
 * A {@code WARN} log is emitted on every failed attempt.
 */
@Slf4j
public class MetricsBuffer {

    private final LinkedBlockingQueue<MetricPushRequest> queue = new LinkedBlockingQueue<>();
    private final int maxSize;
    private final MonitoringServerClient client;

    public MetricsBuffer(MonitoringProperties props, MonitoringServerClient client) {
        this.maxSize = props.getBufferMaxSize();
        this.client = client;
    }

    /**
     * Adds a metric to the buffer. If the queue size reaches {@code bufferMaxSize},
     * an immediate synchronous flush is triggered.
     */
    public void add(MetricPushRequest request) {
        queue.offer(request);
        if (queue.size() >= maxSize) {
            flush();
        }
    }

    /**
     * Drains all buffered metrics and sends them as a single batch.
     * No-op when the buffer is empty. Thread-safe via {@code synchronized}.
     */
    public synchronized void flush() {
        if (queue.isEmpty()) {
            return;
        }
        List<MetricPushRequest> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (batch.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            client.pushMetricBatch(batch);
            log.debug("flushed {} metrics in {}ms", batch.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Failed to flush {} metrics to monitoring-server, re-queuing: {}", batch.size(), e.getMessage());
            queue.addAll(batch);
        }
    }

    /** Flushes remaining metrics on JVM shutdown so no data is lost at graceful stop. */
    @PreDestroy
    public void shutdown() {
        flush();
    }
}
