package com.nmontytskyi.monitoring.starter;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nmontytskyi.monitoring.annotation.MonitoredEndpoint;
import com.nmontytskyi.monitoring.starter.registration.ServiceRegistrationBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = StarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class StarterIntegrationTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("monitoring.server-url", () -> "http://localhost:" + wm.getPort());
        registry.add("monitoring.service-name", () -> "integration-test-service");
        registry.add("monitoring.service-host", () -> "localhost");
        registry.add("monitoring.service-port", () -> "8081");

        // Must stub BEFORE Spring context starts so @PostConstruct registration succeeds
        wm.stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 99}")));
        wm.stubFor(post(urlEqualTo("/api/metrics/endpoint"))
                .willReturn(aResponse().withStatus(202)));
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    static class TestService {
        @MonitoredEndpoint
        public String hello() {
            return "hello";
        }

        @MonitoredEndpoint
        public void broken() {
            throw new RuntimeException("intentional failure");
        }
    }

    @Autowired
    private ServiceRegistrationBean registrationBean;

    @Autowired
    private TestService testService;

    @BeforeEach
    void restubServer() {
        // WireMockExtension.afterEach resets all stubs after each test — re-add them
        wm.stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 99}")));
        wm.stubFor(post(urlEqualTo("/api/metrics/endpoint"))
                .willReturn(aResponse().withStatus(202)));
    }

    @Test
    void contextLoads_registersServiceOnStartup() {
        // Startup registration used the stub from @DynamicPropertySource — serviceId was set
        assertThat(registrationBean.getServiceId()).isEqualTo(99L);
    }

    @Test
    void monitoredEndpoint_pushesMetricAfterInvocation() {
        testService.hello();

        wm.verify(postRequestedFor(urlEqualTo("/api/metrics/endpoint"))
                .withRequestBody(matchingJsonPath("$.serviceId"))
                .withRequestBody(matchingJsonPath("$.endpoint", containing("TestService"))));
    }

    @Test
    void monitoredEndpoint_exceptionInMethod_pushesDownStatus() {
        try {
            testService.broken();
        } catch (RuntimeException ignored) {
        }

        wm.verify(postRequestedFor(urlEqualTo("/api/metrics/endpoint"))
                .withRequestBody(matchingJsonPath("$.status", equalTo("DOWN"))));
    }
}
