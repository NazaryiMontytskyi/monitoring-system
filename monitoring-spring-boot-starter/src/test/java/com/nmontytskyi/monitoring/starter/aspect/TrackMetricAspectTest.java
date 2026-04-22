package com.nmontytskyi.monitoring.starter.aspect;

import com.nmontytskyi.monitoring.annotation.TrackMetric;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = TrackMetricAspectTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class TrackMetricAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        public TrackMetricAspect trackMetricAspect(MetricsBuffer buffer,
                                                    ServiceRegistrationBean registrationBean) {
            return new TrackMetricAspect(buffer, registrationBean);
        }

        @Bean
        public MetricsBuffer metricsBuffer() {
            return mock(MetricsBuffer.class);
        }

        @Bean
        public ServiceRegistrationBean serviceRegistrationBean() {
            ServiceRegistrationBean bean = mock(ServiceRegistrationBean.class);
            when(bean.getServiceId()).thenReturn(1L);
            return bean;
        }

        @Bean
        public TrackedService trackedService() {
            return new TrackedService();
        }
    }

    static class TrackedService {
        @TrackMetric(name = "orders.created")
        public String create() {
            return "created";
        }

        @TrackMetric(name = "orders.fail")
        public void fail() {
            throw new RuntimeException("boom");
        }

        @TrackMetric(name = "orders.slow")
        public String slow() throws InterruptedException {
            Thread.sleep(50);
            return "slow";
        }
    }

    @Autowired
    private TrackedService service;

    @MockBean
    private MetricsBuffer buffer;

    @MockBean
    private ServiceRegistrationBean registrationBean;

    @Test
    void around_methodSucceeds_recordsUpMetric() {
        when(registrationBean.getServiceId()).thenReturn(1L);

        service.create();

        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(buffer).add(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("orders.created");
        assertThat(captor.getValue().getServiceId()).isEqualTo(1L);
    }

    @Test
    void around_methodThrows_recordsDownMetricAndRethrows() {
        when(registrationBean.getServiceId()).thenReturn(1L);

        assertThatThrownBy(() -> service.fail())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(buffer).add(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void around_timingIsAccurate() throws InterruptedException {
        when(registrationBean.getServiceId()).thenReturn(1L);

        service.slow();

        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(buffer).add(captor.capture());
        assertThat(captor.getValue().getResponseTimeMs()).isGreaterThanOrEqualTo(50L);
    }

    @Test
    void around_propagatesReturnValue() {
        when(registrationBean.getServiceId()).thenReturn(1L);

        String result = service.create();

        assertThat(result).isEqualTo("created");
    }
}
