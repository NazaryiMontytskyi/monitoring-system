package com.nmontytskyi.monitoring.model;

import com.nmontytskyi.monitoring.annotation.Sla;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlaDefinition")
class SlaDefinitionTest {

    /**
     * @Sla has @Target({}) — it can only be used as an attribute of another annotation.
     * To test from(@Sla), a synthetic instance is created via Java Proxy.
     */
    private static Sla createSla(double uptimePercent,
                                 long maxResponseTimeMs,
                                 double maxErrorRatePercent,
                                 String description) {
        return (Sla) Proxy.newProxyInstance(
                Sla.class.getClassLoader(),
                new Class[]{Sla.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "uptimePercent"       -> uptimePercent;
                    case "maxResponseTimeMs"   -> maxResponseTimeMs;
                    case "maxErrorRatePercent" -> maxErrorRatePercent;
                    case "description"         -> description;
                    case "annotationType"      -> Sla.class;
                    default                    -> method.getDefaultValue();
                }
        );
    }

    // ── defaults() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("defaults()")
    class DefaultsTests {

        @Test
        @DisplayName("uptime defaults to 99.9%")
        void defaults_uptimeIs99point9() {
            assertThat(SlaDefinition.defaults().getUptimePercent()).isEqualTo(99.9);
        }

        @Test
        @DisplayName("max response time defaults to 1000ms")
        void defaults_maxResponseTimeIs1000ms() {
            assertThat(SlaDefinition.defaults().getMaxResponseTimeMs()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("max error rate defaults to 5.0%")
        void defaults_maxErrorRateIs5percent() {
            assertThat(SlaDefinition.defaults().getMaxErrorRatePercent()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("description defaults to 'Default SLA'")
        void defaults_descriptionIsDefaultSla() {
            assertThat(SlaDefinition.defaults().getDescription()).isEqualTo("Default SLA");
        }

        @Test
        @DisplayName("each call returns a new independent object with equal values")
        void defaults_eachCallReturnsNewInstance() {
            SlaDefinition first  = SlaDefinition.defaults();
            SlaDefinition second = SlaDefinition.defaults();

            assertThat(first).isNotSameAs(second);
            assertThat(first).isEqualTo(second);
        }
    }

    // ── from(@Sla) ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("from(@Sla)")
    class FromAnnotationTests {

        private final Sla slaAnnotation = createSla(99.5, 500L, 2.0, "Production SLA");

        @Test
        @DisplayName("correctly maps uptimePercent from the annotation")
        void from_mapsUptimePercent() {
            assertThat(SlaDefinition.from(slaAnnotation).getUptimePercent()).isEqualTo(99.5);
        }

        @Test
        @DisplayName("correctly maps maxResponseTimeMs from the annotation")
        void from_mapsMaxResponseTimeMs() {
            assertThat(SlaDefinition.from(slaAnnotation).getMaxResponseTimeMs()).isEqualTo(500L);
        }

        @Test
        @DisplayName("correctly maps maxErrorRatePercent from the annotation")
        void from_mapsMaxErrorRatePercent() {
            assertThat(SlaDefinition.from(slaAnnotation).getMaxErrorRatePercent()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("correctly maps description from the annotation")
        void from_mapsDescription() {
            assertThat(SlaDefinition.from(slaAnnotation).getDescription()).isEqualTo("Production SLA");
        }

        @Test
        @DisplayName("result of from() differs from defaults()")
        void from_producesValuesDifferentFromDefaults() {
            SlaDefinition fromAnnotation = SlaDefinition.from(slaAnnotation);
            SlaDefinition defaults       = SlaDefinition.defaults();

            assertThat(fromAnnotation).isNotEqualTo(defaults);
        }
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder allows setting custom values")
        void builder_setsCustomValues() {
            SlaDefinition definition = SlaDefinition.builder()
                    .uptimePercent(98.0)
                    .maxResponseTimeMs(2000L)
                    .maxErrorRatePercent(10.0)
                    .description("Relaxed SLA")
                    .build();

            assertThat(definition.getUptimePercent()).isEqualTo(98.0);
            assertThat(definition.getMaxResponseTimeMs()).isEqualTo(2000L);
            assertThat(definition.getMaxErrorRatePercent()).isEqualTo(10.0);
            assertThat(definition.getDescription()).isEqualTo("Relaxed SLA");
        }
    }
}
