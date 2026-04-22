package com.nmontytskyi.monitoring.starter.aspect;

import com.nmontytskyi.monitoring.annotation.MonitoredEndpoint;
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
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = AllEndpointsAspectTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class AllEndpointsAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        public AllEndpointsAspect allEndpointsAspect(MetricsBuffer buffer,
                                                      ServiceRegistrationBean registrationBean) {
            return new AllEndpointsAspect(buffer, registrationBean);
        }

        @Bean
        public MetricsBuffer metricsBuffer() {
            return mock(MetricsBuffer.class);
        }

        @Bean
        public ServiceRegistrationBean serviceRegistrationBean() {
            ServiceRegistrationBean bean = mock(ServiceRegistrationBean.class);
            when(bean.getServiceId()).thenReturn(42L);
            return bean;
        }

        @Bean
        public TestRestController testRestController() {
            return new TestRestController();
        }

        @Bean
        public TestPlainService testPlainService() {
            return new TestPlainService();
        }
    }

    @RestController
    static class TestRestController {
        public String hello() {
            return "hello";
        }

        @MonitoredEndpoint
        public String monitored() {
            return "monitored";
        }
    }

    static class TestPlainService {
        public String doWork() {
            return "work";
        }
    }

    @Autowired
    private TestRestController restController;

    @Autowired
    private TestPlainService plainService;

    @MockBean
    private MetricsBuffer buffer;

    @MockBean
    private ServiceRegistrationBean registrationBean;

    @Test
    void restControllerMethod_intercepted() {
        when(registrationBean.getServiceId()).thenReturn(42L);

        restController.hello();

        ArgumentCaptor<MetricPushRequest> captor = ArgumentCaptor.forClass(MetricPushRequest.class);
        verify(buffer).add(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(captor.getValue().getEndpoint()).contains("hello");
    }

    @Test
    void nonRestControllerMethod_notIntercepted() {
        when(registrationBean.getServiceId()).thenReturn(42L);

        plainService.doWork();

        verify(buffer, never()).add(any());
    }

    @Test
    void methodWithMonitoredEndpoint_notDoubleIntercepted() {
        when(registrationBean.getServiceId()).thenReturn(42L);

        restController.monitored();

        // AllEndpointsAspect skips @MonitoredEndpoint methods
        verify(buffer, never()).add(any());
    }

    @Test
    void serviceIdNull_metricsNotBuffered() {
        when(registrationBean.getServiceId()).thenReturn(null);

        restController.hello();

        verify(buffer, never()).add(any());
    }
}
