package com.nmontytskyi.monitoring.starter.env;

import com.nmontytskyi.monitoring.annotation.Sla;
import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers {@link MonitoredMicroservice} annotation attributes as the lowest-priority
 * {@link org.springframework.core.env.PropertySource} in the application environment.
 *
 * <p>Running as an {@code EnvironmentPostProcessor} guarantees the values are present
 * before any {@code @ConditionalOnProperty}, {@code @ConfigurationProperties} binding,
 * or {@code @Scheduled} fixedDelay resolution takes place.
 * Any {@code application.yml} key under the {@code monitoring.*} prefix naturally overrides
 * the annotation value because YAML property sources have higher precedence.
 *
 * <p>Registered via {@code META-INF/spring.factories}.
 */
public class MonitoredMicroserviceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "monitoredMicroserviceAnnotation";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        MonitoredMicroservice annotation = findAnnotation(application);
        if (annotation == null) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        props.put("monitoring.service-name", annotation.name());
        props.put("monitoring.server-url", annotation.serverUrl());
        props.put("monitoring.track-all-endpoints", annotation.trackAllEndpoints());
        props.put("monitoring.buffer-flush-interval-ms", annotation.bufferFlushIntervalMs());
        props.put("monitoring.buffer-max-size", annotation.bufferMaxSize());

        Sla sla = annotation.sla();
        props.put("monitoring.sla-response-time-ms", sla.maxResponseTimeMs());
        props.put("monitoring.sla-uptime-percent", sla.uptimePercent());
        props.put("monitoring.sla-error-rate-percent", sla.maxErrorRatePercent());

        // addLast → lowest priority: application.yml and system properties always win
        environment.getPropertySources().addLast(
                new MapPropertySource(PROPERTY_SOURCE_NAME, props));
    }

    /**
     * Locates the {@link MonitoredMicroservice} annotation by first checking
     * {@link SpringApplication#getMainApplicationClass()} (works for production runs), then
     * scanning {@link SpringApplication#getAllSources()} (works for {@code @SpringBootTest}).
     */
    private MonitoredMicroservice findAnnotation(SpringApplication application) {
        Class<?> mainClass = application.getMainApplicationClass();
        if (mainClass != null) {
            MonitoredMicroservice ann =
                    AnnotationUtils.findAnnotation(mainClass, MonitoredMicroservice.class);
            if (ann != null) {
                return ann;
            }
        }
        // Fallback: iterate primary sources — needed when running under @SpringBootTest
        for (Object source : application.getAllSources()) {
            if (source instanceof Class<?> cls) {
                MonitoredMicroservice ann =
                        AnnotationUtils.findAnnotation(cls, MonitoredMicroservice.class);
                if (ann != null) {
                    return ann;
                }
            }
        }
        return null;
    }
}
