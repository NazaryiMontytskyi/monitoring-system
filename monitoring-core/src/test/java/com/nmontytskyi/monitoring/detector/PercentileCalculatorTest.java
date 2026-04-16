package com.nmontytskyi.monitoring.detector;

import com.nmontytskyi.monitoring.detector.PercentileCalculator.PercentileStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PercentileCalculator")
class PercentileCalculatorTest {

    // 10 values: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    private static final List<Long> TEN_VALUES = List.of(
            10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L
    );

    // 100 values: [1, 2, ..., 100]
    private static final List<Long> HUNDRED_VALUES;
    static {
        List<Long> values = new ArrayList<>();
        for (long i = 1; i <= 100; i++) values.add(i);
        HUNDRED_VALUES = List.copyOf(values);
    }

    // ── P50 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("p50")
    class P50Tests {

        @Test
        @DisplayName("returns the median for 10 values")
        void p50_withTenValues_returnsMedian() {
            // ceil(0.50 * 10) - 1 = 4 → sorted[4] = 50
            assertThat(PercentileCalculator.p50(TEN_VALUES)).isEqualTo(50L);
        }

        @Test
        @DisplayName("returns the median for 100 values")
        void p50_withHundredValues_returnsMedian() {
            // ceil(0.50 * 100) - 1 = 49 → sorted[49] = 50
            assertThat(PercentileCalculator.p50(HUNDRED_VALUES)).isEqualTo(50L);
        }

        @Test
        @DisplayName("returns the single value for a one-element list")
        void p50_withSingleValue_returnsThatValue() {
            assertThat(PercentileCalculator.p50(List.of(42L))).isEqualTo(42L);
        }
    }

    // ── P95 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("p95")
    class P95Tests {

        @Test
        @DisplayName("returns the correct P95 for 100 values")
        void p95_withHundredValues_returnsCorrectValue() {
            // ceil(0.95 * 100) - 1 = 94 → sorted[94] = 95
            assertThat(PercentileCalculator.p95(HUNDRED_VALUES)).isEqualTo(95L);
        }

        @Test
        @DisplayName("P95 is greater than or equal to P50")
        void p95_isGreaterOrEqualToP50() {
            assertThat(PercentileCalculator.p95(HUNDRED_VALUES))
                    .isGreaterThanOrEqualTo(PercentileCalculator.p50(HUNDRED_VALUES));
        }
    }

    // ── P99 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("p99")
    class P99Tests {

        @Test
        @DisplayName("returns the correct P99 for 100 values")
        void p99_withHundredValues_returnsCorrectValue() {
            // ceil(0.99 * 100) - 1 = 98 → sorted[98] = 99
            assertThat(PercentileCalculator.p99(HUNDRED_VALUES)).isEqualTo(99L);
        }

        @Test
        @DisplayName("P99 is greater than or equal to P95")
        void p99_isGreaterOrEqualToP95() {
            assertThat(PercentileCalculator.p99(HUNDRED_VALUES))
                    .isGreaterThanOrEqualTo(PercentileCalculator.p95(HUNDRED_VALUES));
        }
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sorting before calculation")
    class SortingTests {

        @Test
        @DisplayName("produces identical results regardless of input order")
        void calculate_withUnsortedInput_sortsBeforeCalculating() {
            List<Long> unsorted = List.of(50L, 10L, 90L, 30L, 70L, 20L, 80L, 40L, 60L, 100L);
            List<Long> sorted   = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);

            assertThat(PercentileCalculator.p50(unsorted)).isEqualTo(PercentileCalculator.p50(sorted));
            assertThat(PercentileCalculator.p95(unsorted)).isEqualTo(PercentileCalculator.p95(sorted));
            assertThat(PercentileCalculator.p99(unsorted)).isEqualTo(PercentileCalculator.p99(sorted));
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("returns 0 for an empty list")
        void calculate_withEmptyList_returnsZero() {
            assertThat(PercentileCalculator.p50(List.of())).isEqualTo(0L);
            assertThat(PercentileCalculator.p95(List.of())).isEqualTo(0L);
            assertThat(PercentileCalculator.p99(List.of())).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns 0 for a null list")
        void calculate_withNullList_returnsZero() {
            assertThat(PercentileCalculator.calculate(null, 50)).isEqualTo(0L);
        }

        @Test
        @DisplayName("P0 returns the minimum value")
        void calculate_p0_returnsMinimum() {
            assertThat(PercentileCalculator.calculate(TEN_VALUES, 0)).isEqualTo(10L);
        }

        @Test
        @DisplayName("P100 returns the maximum value")
        void calculate_p100_returnsMaximum() {
            assertThat(PercentileCalculator.calculate(TEN_VALUES, 100)).isEqualTo(100L);
        }

        @Test
        @DisplayName("throws exception when percentile is below 0")
        void calculate_withNegativePercentile_throwsException() {
            assertThatThrownBy(() -> PercentileCalculator.calculate(TEN_VALUES, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-1");
        }

        @Test
        @DisplayName("throws exception when percentile is above 100")
        void calculate_withPercentileAbove100_throwsException() {
            assertThatThrownBy(() -> PercentileCalculator.calculate(TEN_VALUES, 101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("101");
        }
    }

    // ── calculateAll ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateAll")
    class CalculateAllTests {

        @Test
        @DisplayName("returns values consistent with individual p50/p95/p99 methods")
        void calculateAll_returnsConsistentValuesWithIndividualMethods() {
            PercentileStats stats = PercentileCalculator.calculateAll(HUNDRED_VALUES);

            assertThat(stats.getP50()).isEqualTo(PercentileCalculator.p50(HUNDRED_VALUES));
            assertThat(stats.getP95()).isEqualTo(PercentileCalculator.p95(HUNDRED_VALUES));
            assertThat(stats.getP99()).isEqualTo(PercentileCalculator.p99(HUNDRED_VALUES));
        }

        @Test
        @DisplayName("returns all zeros for an empty list")
        void calculateAll_withEmptyList_returnsAllZeros() {
            PercentileStats stats = PercentileCalculator.calculateAll(List.of());

            assertThat(stats.getP50()).isEqualTo(0L);
            assertThat(stats.getP95()).isEqualTo(0L);
            assertThat(stats.getP99()).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns all zeros for a null list")
        void calculateAll_withNull_returnsAllZeros() {
            PercentileStats stats = PercentileCalculator.calculateAll(null);

            assertThat(stats.getP50()).isEqualTo(0L);
            assertThat(stats.getP95()).isEqualTo(0L);
            assertThat(stats.getP99()).isEqualTo(0L);
        }
    }

    // ── PercentileStats ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("PercentileStats")
    class PercentileStatsTests {

        @Test
        @DisplayName("of() preserves the provided values")
        void of_preservesValues() {
            PercentileStats stats = PercentileStats.of(50L, 95L, 99L);

            assertThat(stats.getP50()).isEqualTo(50L);
            assertThat(stats.getP95()).isEqualTo(95L);
            assertThat(stats.getP99()).isEqualTo(99L);
        }

        @Test
        @DisplayName("empty() returns all zeros")
        void empty_returnsZeros() {
            PercentileStats stats = PercentileStats.empty();

            assertThat(stats.getP50()).isEqualTo(0L);
            assertThat(stats.getP95()).isEqualTo(0L);
            assertThat(stats.getP99()).isEqualTo(0L);
        }

        @Test
        @DisplayName("toString() contains percentile values and unit")
        void toString_containsPercentilesAndUnit() {
            PercentileStats stats = PercentileStats.of(50L, 340L, 1200L);
            String str = stats.toString();

            assertThat(str).contains("50", "340", "1200", "ms");
        }
    }
}
