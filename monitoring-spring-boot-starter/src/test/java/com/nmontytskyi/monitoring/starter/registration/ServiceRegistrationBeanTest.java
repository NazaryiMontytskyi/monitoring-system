package com.nmontytskyi.monitoring.starter.registration;

import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.starter.config.MonitoringProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRegistrationBeanTest {

    @Mock
    private MonitoringServerClient client;

    @Mock
    private MonitoringProperties props;

    @InjectMocks
    private ServiceRegistrationBean bean;

    @Test
    void postConstruct_success_storesServiceId() {
        when(props.getServiceName()).thenReturn("my-service");
        when(props.getServiceHost()).thenReturn("localhost");
        when(props.getServicePort()).thenReturn(8081);
        when(props.getActuatorUrl()).thenReturn("http://localhost:8081/actuator");
        when(client.registerService(any(ServiceRegistrationRequest.class))).thenReturn(42L);

        bean.register();

        assertThat(bean.getServiceId()).isEqualTo(42L);
    }

    @Test
    void postConstruct_clientReturnsNull_serviceIdIsNull() {
        when(props.getServiceName()).thenReturn("my-service");
        when(props.getServiceHost()).thenReturn("localhost");
        when(props.getServicePort()).thenReturn(8081);
        when(props.getActuatorUrl()).thenReturn(null);
        when(client.registerService(any(ServiceRegistrationRequest.class))).thenReturn(null);

        bean.register();

        assertThat(bean.getServiceId()).isNull();
    }

    @Test
    void postConstruct_doesNotThrow_whenClientFails() {
        when(props.getServiceName()).thenReturn("my-service");
        when(props.getServiceHost()).thenReturn("localhost");
        when(props.getServicePort()).thenReturn(8081);
        when(props.getActuatorUrl()).thenReturn(null);
        doThrow(new RuntimeException("connection refused")).when(client).registerService(any());

        assertThatNoException().isThrownBy(() -> bean.register());
        assertThat(bean.getServiceId()).isNull();
    }
}
