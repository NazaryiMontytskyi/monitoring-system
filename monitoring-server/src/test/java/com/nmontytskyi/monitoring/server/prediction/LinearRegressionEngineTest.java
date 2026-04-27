package com.nmontytskyi.monitoring.server.prediction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LinearRegressionEngineTest {

    @Test
    void risingTrend_predictedValueApproximatesNextStep() {
        List<Double> timestamps = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

        LinearRegressionEngine.PredictionResult result =
                LinearRegressionEngine.predict(timestamps, values, 1.0);

        assertThat(result.predictedValue()).isCloseTo(60.0, within(1.0));
        assertThat(result.slope()).isPositive();
        assertThat(result.rSquared()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void fallingTrend_slopeIsNegative() {
        List<Double> timestamps = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> values = List.of(50.0, 40.0, 30.0, 20.0, 10.0);

        LinearRegressionEngine.PredictionResult result =
                LinearRegressionEngine.predict(timestamps, values, 1.0);

        assertThat(result.slope()).isNegative();
        assertThat(result.predictedValue()).isCloseTo(0.0, within(1.0));
        assertThat(result.rSquared()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void constantValues_rSquaredIsOneAndSlopeIsZero() {
        List<Double> timestamps = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> values = List.of(42.0, 42.0, 42.0, 42.0, 42.0);

        LinearRegressionEngine.PredictionResult result =
                LinearRegressionEngine.predict(timestamps, values, 10.0);

        assertThat(result.rSquared()).isEqualTo(1.0);
        assertThat(result.slope()).isEqualTo(0.0);
        assertThat(result.predictedValue()).isCloseTo(42.0, within(0.001));
    }

    @Test
    void twoDataPoints_doesNotThrow() {
        List<Double> timestamps = List.of(1.0, 2.0);
        List<Double> values = List.of(10.0, 20.0);

        LinearRegressionEngine.PredictionResult result =
                LinearRegressionEngine.predict(timestamps, values, 1.0);

        assertThat(result).isNotNull();
        assertThat(result.predictedValue()).isCloseTo(30.0, within(1.0));
    }

    @Test
    void chaoticData_rSquaredIsBelowThreshold() {
        List<Double> timestamps = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0);
        List<Double> values = List.of(10.0, 90.0, 5.0, 80.0, 15.0, 70.0, 20.0, 60.0);

        LinearRegressionEngine.PredictionResult result =
                LinearRegressionEngine.predict(timestamps, values, 5.0);

        assertThat(result.rSquared()).isLessThan(0.3);
    }

    @Test
    void singleDataPoint_throwsException() {
        assertThatThrownBy(() ->
                LinearRegressionEngine.predict(List.of(1.0), List.of(10.0), 5.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2");
    }
}
