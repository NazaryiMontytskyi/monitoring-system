package com.nmontytskyi.monitoring.starter.buffer;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.config.MonitoringProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsBufferTest {

    @Mock
    private MonitoringServerClient client;

    private MonitoringProperties props;

    @BeforeEach
    void setUp() {
        props = new MonitoringProperties();
        props.setBufferFlushIntervalMs(60_000); // disabled for unit tests
    }

    private MetricPushRequest buildRequest() {
        return MetricPushRequest.builder()
                .serviceId(1L)
                .endpoint("test.endpoint")
                .responseTimeMs(100)
                .status(HealthStatus.UP)
                .build();
    }

    @Test
    void add_belowMaxSize_doesNotFlush() {
        props.setBufferMaxSize(5);
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        for (int i = 0; i < 4; i++) {
            buffer.add(buildRequest());
        }

        verify(client, never()).pushMetricBatch(anyList());
    }

    @Test
    void add_reachesMaxSize_triggersImmediateFlush() {
        props.setBufferMaxSize(5);
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        for (int i = 0; i < 5; i++) {
            buffer.add(buildRequest());
        }

        verify(client, times(1)).pushMetricBatch(anyList());
    }

    @Test
    void scheduledFlush_emptyBuffer_noHttpCall() {
        props.setBufferMaxSize(100);
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        buffer.flush();

        verify(client, never()).pushMetricBatch(anyList());
    }

    @Test
    void scheduledFlush_withEntries_sendsBatch() {
        props.setBufferMaxSize(100);
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        buffer.add(buildRequest());
        buffer.add(buildRequest());
        buffer.flush();

        ArgumentCaptor<List<MetricPushRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).pushMetricBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void concurrentAdds_noLostMetrics() throws InterruptedException {
        props.setBufferMaxSize(2000); // large enough to not auto-flush
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        int threads = 10;
        int perThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < perThread; j++) {
                    buffer.add(buildRequest());
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        buffer.flush();

        ArgumentCaptor<List<MetricPushRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).pushMetricBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(threads * perThread);
    }

    @Test
    void preDestroy_flushesRemaining() {
        props.setBufferMaxSize(100);
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        buffer.add(buildRequest());
        buffer.add(buildRequest());
        buffer.shutdown();

        verify(client).pushMetricBatch(anyList());
    }

    @Test
    void httpCallFails_metricsRequeued() {
        props.setBufferMaxSize(100);
        MetricsBuffer buffer = new MetricsBuffer(props, client);

        doThrow(new RuntimeException("connection refused")).when(client).pushMetricBatch(anyList());

        buffer.add(buildRequest());
        buffer.flush(); // first attempt — fails, re-queues

        // second flush should try again with the same item
        reset(client); // stop throwing
        buffer.flush();

        verify(client).pushMetricBatch(argThat(list -> list.size() == 1));
    }
}
