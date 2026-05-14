package com.nmontytskyi.monitoring.server.config;

import com.nmontytskyi.monitoring.detector.AnomalyDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@code @Configuration} class that registers beans from the {@code monitoring-core}
 * module into the server's application context.
 *
 * <p>Primarily instantiates the {@link com.nmontytskyi.monitoring.detector.AnomalyDetector}
 * with the server-side anomaly threshold so it can be injected into
 * {@link com.nmontytskyi.monitoring.server.service.MetricsPersistenceService}.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.detector.AnomalyDetector
 */
@Configuration
public class MonitoringCoreConfig {

    @Bean
    public AnomalyDetector anomalyDetector() {
        return new AnomalyDetector();
    }
}
