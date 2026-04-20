package com.nmontytskyi.monitoring.starter.aspect;

import com.nmontytskyi.monitoring.annotation.MonitoredEndpoint;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MonitoredEndpointAspectTest.TestConfig.class)
class MonitoredEndpointAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        public MonitoringServerClient monitoringServerClient() {
            return mock(MonitoringServerClient.class);
        }

        @Bean
        public ServiceRegistrationBean serviceRegistrationBean() {
            return mock(ServiceRegistrationBean.class);
        }

        @Bean
        public MonitoredEndpointAspect monitoredEndpointAspect(ServiceRegistrationBean bean,
                                                                MonitoringServerClient client) {
            return new MonitoredEndpointAspect(bean, client);
        }

        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    static class TestService {
        @MonitoredEndpoint
        public String work() {
            return "ok";
        }

        @MonitoredEndpoint
        public void fail() {
            throw new RuntimeException("oops");
        }

        @MonitoredEndpoint
        public String slowWork() throws InterruptedException {
            Thread.sleep(100);
            return "done";
        }
    }

    @Autowired
    private TestService testService;

    @Autowired
    private ServiceRegistrationBean registrationBean;

    @Autowired
    private MonitoringServerClient client;

    @BeforeEach
    void setUp() {
        reset(registrationBean, client);
    }

    @Test
    void around_successfulMethod_pushesUpMetric() {
        when(registrationBean.getServiceId()).thenReturn(1L);

        String result = testService.work();

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(client).pushMetric(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(captor.getValue().getServiceId()).isEqualTo(1L);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("TestService.work");
    }

    @Test
    void around_throwingMethod_pushesDownMetric() {
        when(registrationBean.getServiceId()).thenReturn(1L);

        assertThatThrownBy(() -> testService.fail())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("oops");

        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(client).pushMetric(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("oops");
    }

    @Test
    void around_nullServiceId_skipsMetricPush() {
        when(registrationBean.getServiceId()).thenReturn(null);

        testService.work();

        verify(client, never()).pushMetric(any());
    }

    @Test
    void around_measuresResponseTime() throws InterruptedException {
        when(registrationBean.getServiceId()).thenReturn(1L);

        testService.slowWork();

        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(client).pushMetric(captor.capture());
        assertThat(captor.getValue().getResponseTimeMs()).isGreaterThanOrEqualTo(100L);
    }

    @Test
    void around_pushFails_doesNotAffectOriginalMethod() {
        when(registrationBean.getServiceId()).thenReturn(1L);
        doThrow(new RuntimeException("push failed")).when(client).pushMetric(any());

        String result = testService.work();

        assertThat(result).isEqualTo("ok");
    }
}
