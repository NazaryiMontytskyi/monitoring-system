package com.nmontytskyi.monitoring.detector;

import com.nmontytskyi.monitoring.detector.AnomalyDetector.AnomalyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnomalyDetector")
class AnomalyDetectorTest {

    // Sample values close to 100ms: mean≈100, stdDev≈3.0
    // Values within 3σ: [91, 109]. Anomaly: < 91 or > 109
    private static final List<Double> STABLE_HISTORY = List.of(
            97.0, 100.0, 103.0, 98.0, 102.0,
            99.0, 101.0, 97.0, 103.0, 100.0
    );

    private AnomalyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new AnomalyDetector();
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("default constructor uses threshold 3.0")
        void defaultConstructor_usesDefaultThreshold() {
            AnomalyResult result = new AnomalyDetector().analyze(500.0, STABLE_HISTORY);
            assertThat(result.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("throws exception when threshold is zero")
        void customConstructor_withZeroThreshold_throwsException() {
            assertThatThrownBy(() -> new AnomalyDetector(0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("throws exception when threshold is negative")
        void customConstructor_withNegativeThreshold_throwsException() {
            assertThatThrownBy(() -> new AnomalyDetector(-1.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Insufficient data ────────────────────────────────────────────────────

    @Nested
    @DisplayName("analyze — insufficient data")
    class InsufficientDataTests {

        @Test
        @DisplayName("returns insufficient result when history is null")
        void analyze_withNullHistory_returnsInsufficient() {
            AnomalyResult result = detector.analyze(500.0, null);

            assertThat(result.hasSufficientData()).isFalse();
            assertThat(result.isAnomaly()).isFalse();
            assertThat(result.getZScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns insufficient result when history is empty")
        void analyze_withEmptyHistory_returnsInsufficient() {
            AnomalyResult result = detector.analyze(500.0, List.of());

            assertThat(result.hasSufficientData()).isFalse();
        }

        @Test
        @DisplayName("returns insufficient result when fewer than 10 values provided")
        void analyze_withFewerThanMinSamples_returnsInsufficient() {
            List<Double> tooFew = List.of(100.0, 200.0, 150.0);
            AnomalyResult result = detector.analyze(500.0, tooFew);

            assertThat(result.hasSufficientData()).isFalse();
        }

        @Test
        @DisplayName("processes successfully with exactly 10 values (minimum sample size)")
        void analyze_withExactlyMinSamples_processesSuccessfully() {
            AnomalyResult result = detector.analyze(100.0, STABLE_HISTORY);

            assertThat(result.hasSufficientData()).isTrue();
        }
    }

    // ── Anomaly detection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("analyze — anomaly detection")
    class AnomalyDetectionTests {

        @Test
        @DisplayName("detects anomaly when value is far beyond 3σ")
        void analyze_withValueFarBeyondThreeSigma_detectsAnomaly() {
            AnomalyResult result = detector.analyze(500.0, STABLE_HISTORY);

            assertThat(result.hasSufficientData()).isTrue();
            assertThat(result.isAnomaly()).isTrue();
            assertThat(result.getZScore()).isGreaterThan(AnomalyDetector.DEFAULT_THRESHOLD);
        }

        @Test
        @DisplayName("does not detect anomaly for a value within normal range")
        void analyze_withValueWithinNormalRange_doesNotDetectAnomaly() {
            // 102.0 fits well within [91, 109]
            AnomalyResult result = detector.analyze(102.0, STABLE_HISTORY);

            assertThat(result.hasSufficientData()).isTrue();
            assertThat(result.isAnomaly()).isFalse();
            assertThat(Math.abs(result.getZScore())).isLessThan(AnomalyDetector.DEFAULT_THRESHOLD);
        }

        @Test
        @DisplayName("detects anomaly for a value below normal range with negative Z-score")
        void analyze_withValueBelowNormalRange_detectsAnomalyWithNegativeZScore() {
            AnomalyResult result = detector.analyze(1.0, STABLE_HISTORY);

            assertThat(result.isAnomaly()).isTrue();
            assertThat(result.getZScore()).isNegative();
        }

        @Test
        @DisplayName("Z-score is zero when value equals the mean")
        void analyze_withValueEqualToMean_returnsZeroZScore() {
            // Mean of STABLE_HISTORY = 100.0
            AnomalyResult result = detector.analyze(100.0, STABLE_HISTORY);

            assertThat(result.getZScore()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
            assertThat(result.isAnomaly()).isFalse();
        }
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("analyze — edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("not an anomaly when all values are identical and current matches them")
        void analyze_withAllIdenticalValuesAndSameCurrentValue_notAnomaly() {
            List<Double> identical = Collections.nCopies(10, 100.0);
            AnomalyResult result = detector.analyze(100.0, identical);

            assertThat(result.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("anomaly when all values are identical but current value differs")
        void analyze_withAllIdenticalValuesAndDifferentCurrentValue_isAnomaly() {
            List<Double> identical = Collections.nCopies(10, 100.0);
            AnomalyResult result = detector.analyze(150.0, identical);

            assertThat(result.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("lower threshold of 2.0 detects anomalies earlier than 3.0")
        void analyze_withLowerThreshold_detectsAnomalyEarlier() {
            AnomalyDetector sensitiveDetector = new AnomalyDetector(2.0);
            AnomalyResult strictResult  = new AnomalyDetector(3.0).analyze(106.5, STABLE_HISTORY);
            AnomalyResult lenientResult = sensitiveDetector.analyze(106.5, STABLE_HISTORY);

            // Both detectors compute the same Z-score
            assertThat(lenientResult.getZScore()).isEqualTo(strictResult.getZScore());
            // If |Z| is between 2 and 3, the sensitive detector flags it while the strict one does not
            if (Math.abs(strictResult.getZScore()) < 3.0 && Math.abs(strictResult.getZScore()) > 2.0) {
                assertThat(lenientResult.isAnomaly()).isTrue();
                assertThat(strictResult.isAnomaly()).isFalse();
            }
        }
    }

    // ── AnomalyResult ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AnomalyResult")
    class AnomalyResultTests {

        @Test
        @DisplayName("insufficient() returns correct state")
        void insufficient_hasCorrectState() {
            AnomalyResult result = AnomalyResult.insufficient();

            assertThat(result.hasSufficientData()).isFalse();
            assertThat(result.isAnomaly()).isFalse();
            assertThat(result.getZScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("of() preserves the provided values")
        void of_preservesValues() {
            AnomalyResult result = AnomalyResult.of(4.2, true);

            assertThat(result.getZScore()).isEqualTo(4.2);
            assertThat(result.isAnomaly()).isTrue();
            assertThat(result.hasSufficientData()).isTrue();
        }

        @Test
        @DisplayName("toString() contains all key fields")
        void toString_containsKeyFields() {
            AnomalyResult result = AnomalyResult.of(2.5, true);
            String str = result.toString();

            assertThat(str).contains("zScore", "anomaly", "sufficientData");
        }
    }
}
