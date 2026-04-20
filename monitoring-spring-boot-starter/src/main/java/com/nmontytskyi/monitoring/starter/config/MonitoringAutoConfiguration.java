package com.nmontytskyi.monitoring.starter.config;

import com.nmontytskyi.monitoring.starter.aspect.MonitoredEndpointAspect;
import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@ConditionalOnProperty(
        prefix = "monitoring",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableAspectJAutoProxy
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MonitoringServerClient monitoringServerClient(MonitoringProperties props) {
        return new MonitoringServerClient(props.getServerUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistrationBean serviceRegistrationBean(MonitoringServerClient client,
                                                           MonitoringProperties props) {
        return new ServiceRegistrationBean(client, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitoredEndpointAspect monitoredEndpointAspect(ServiceRegistrationBean registrationBean,
                                                           MonitoringServerClient client) {
        return new MonitoredEndpointAspect(registrationBean, client);
    }
}
