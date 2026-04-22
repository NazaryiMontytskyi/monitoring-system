package com.nmontytskyi.monitoring.starter.config;

import com.nmontytskyi.monitoring.starter.aspect.AllEndpointsAspect;
import com.nmontytskyi.monitoring.starter.aspect.MonitoredEndpointAspect;
import com.nmontytskyi.monitoring.starter.aspect.TrackMetricAspect;
import com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer;
import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

/**
 * Auto-configuration for the monitoring starter.
 *
 * <p>Activated in two ways:
 * <ol>
 *   <li>Via {@code META-INF/spring/AutoConfiguration.imports} — uses {@code application.yml}
 *       properties under the {@code monitoring.*} prefix.</li>
 *   <li>Via {@code @MonitoredMicroservice} on the main application class — annotation attribute
 *       values are registered as the lowest-priority
 *       {@link org.springframework.core.env.PropertySource} by
 *       {@link com.nmontytskyi.monitoring.starter.env.MonitoredMicroserviceEnvironmentPostProcessor}
 *       before any bean processing takes place, so {@code application.yml} always overrides them.</li>
 * </ol>
 *
 * <p>The cascade is: annotation defaults → overridden by YAML → overridden by system properties.
 * This means {@code @ConditionalOnProperty}, {@code @Scheduled}, and {@code @ConfigurationProperties}
 * all see the correct effective value without any special handling here.
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "monitoring",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableAspectJAutoProxy
@EnableScheduling
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

    @Bean
    @ConditionalOnMissingBean
    public MetricsBuffer metricsBuffer(MonitoringProperties props, MonitoringServerClient client) {
        return new MetricsBuffer(props, client);
    }

    @Bean
    @ConditionalOnMissingBean
    public TrackMetricAspect trackMetricAspect(MetricsBuffer buffer,
                                               ServiceRegistrationBean registrationBean) {
        return new TrackMetricAspect(buffer, registrationBean);
    }

    @Bean
    @ConditionalOnProperty(prefix = "monitoring", name = "track-all-endpoints", havingValue = "true")
    @ConditionalOnMissingBean
    public AllEndpointsAspect allEndpointsAspect(MetricsBuffer buffer,
                                                 ServiceRegistrationBean registrationBean) {
        return new AllEndpointsAspect(buffer, registrationBean);
    }

    /**
     * Drives the periodic flush of {@link MetricsBuffer} at the configured interval.
     * Using {@link SchedulingConfigurer} reads the interval from the fully-bound
     * {@link MonitoringProperties} bean rather than a raw property string, so the
     * annotation's default is applied correctly when no YAML override is present.
     */
    @Bean
    @ConditionalOnMissingBean(SchedulingConfigurer.class)
    public SchedulingConfigurer metricsBufferScheduler(MetricsBuffer buffer,
                                                       MonitoringProperties props) {
        return registrar -> registrar.addFixedDelayTask(
                buffer::flush,
                props.getBufferFlushIntervalMs());
    }
}
