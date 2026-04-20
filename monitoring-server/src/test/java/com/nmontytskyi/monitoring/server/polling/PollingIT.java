package com.nmontytskyi.monitoring.server.polling;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class PollingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RegisteredServiceRepository serviceRepository;

    @Autowired
    private MetricRecordRepository metricRecordRepository;

    @Autowired
    private MetricsPollingScheduler pollingScheduler;

    @BeforeEach
    void resetWireMockStubs() {
        wireMock.resetAll();
    }

    @Test
    void scheduledPolling_savesMetricsWithPullSource() {
        wireMock.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(okJson("{\"status\":\"UP\"}")));
        wireMock.stubFor(get(urlMatching("/actuator/metrics/.*"))
                .willReturn(okJson("""
                        {"name":"jvm.memory.used","measurements":[{"statistic":"VALUE","value":52428800}]}
                        """)));

        RegisteredServiceEntity service = serviceRepository.save(RegisteredServiceEntity.builder()
                .name("poll-it-service")
                .host("localhost")
                .port(wireMock.getPort())
                .actuatorUrl(wireMock.baseUrl() + "/actuator")
                .baseUrl(wireMock.baseUrl())
                .status(HealthStatus.UNKNOWN)
                .build());

        pollingScheduler.pollAllServices();

        List<MetricRecordEntity> records = metricRecordRepository.findAll().stream()
                .filter(r -> r.getService().getId().equals(service.getId()))
                .toList();
        assertThat(records).isNotEmpty();
        assertThat(records).allMatch(r -> r.getSource() == MetricRecordEntity.MetricSource.PULL);
    }

    @Test
    void polling_whenServiceDown_updatesServiceStatus() {
        wireMock.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(okJson("{\"status\":\"DOWN\"}")));

        RegisteredServiceEntity service = serviceRepository.save(RegisteredServiceEntity.builder()
                .name("down-it-service")
                .host("localhost")
                .port(wireMock.getPort())
                .actuatorUrl(wireMock.baseUrl() + "/actuator")
                .baseUrl(wireMock.baseUrl())
                .status(HealthStatus.UNKNOWN)
                .build());

        pollingScheduler.pollAllServices();

        RegisteredServiceEntity updated = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(HealthStatus.DOWN);
    }
}
