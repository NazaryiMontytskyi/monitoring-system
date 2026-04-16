package com.nmontytskyi.monitoring.detector;

import java.util.Comparator;
import java.util.List;

/**
 * Utility class for calculating response time percentiles.
 *
 * <p>Percentile P95 = 340ms means that 95% of requests completed faster than 340ms,
 * while 5% were slower. This is a more accurate indicator than the average,
 * which hides "tail" latencies that affect real user experience.
 *
 * <p>Implements the Nearest Rank Method:
 * <pre>
 *   index  = ceil(p / 100 * n) - 1
 *   result = sortedValues[index]
 * </pre>
 *
 * <p>This is a utility class (static methods only) and cannot be instantiated.
 */
public final class PercentileCalculator {

    private PercentileCalculator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Calculates the given percentile for a list of response time values.
     *
     * @param values     list of measurements in milliseconds (does not need to be sorted)
     * @param percentile target percentile from 0 to 100 inclusive
     * @return the percentile value; {@code 0} if the list is empty
     * @throws IllegalArgumentException if {@code percentile} is outside the range [0, 100]
     */
    public static long calculate(List<Long> values, int percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException(
                    "Percentile must be in range [0, 100], got: " + percentile);
        }
        if (values == null || values.isEmpty()) {
            return 0L;
        }

        List<Long> sorted = values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        if (percentile == 0) {
            return sorted.get(0);
        }
        if (percentile == 100) {
            return sorted.get(sorted.size() - 1);
        }

        // Nearest Rank Method
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(index);
    }

    /**
     * Calculates the median (P50) — 50% of requests completed faster than this value.
     *
     * @param values list of measurements in milliseconds
     * @return median or {@code 0} if the list is empty
     */
    public static long p50(List<Long> values) {
        return calculate(values, 50);
    }

    /**
     * Calculates P95 — 95% of requests completed faster than this value.
     * Standard indicator for API performance evaluation.
     *
     * @param values list of measurements in milliseconds
     * @return P95 or {@code 0} if the list is empty
     */
    public static long p95(List<Long> values) {
        return calculate(values, 95);
    }

    /**
     * Calculates P99 — 99% of requests completed faster than this value.
     * Represents "tail latency" — the experience of the slowest 1% of requests.
     *
     * @param values list of measurements in milliseconds
     * @return P99 or {@code 0} if the list is empty
     */
    public static long p99(List<Long> values) {
        return calculate(values, 99);
    }

    /**
     * Calculates P50, P95, and P99 in a single sorting pass.
     * Recommended method for populating {@link com.nmontytskyi.monitoring.model.SlaReport}.
     *
     * @param values list of measurements in milliseconds
     * @return object containing all three percentiles; all zeros if the list is empty
     */
    public static PercentileStats calculateAll(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return PercentileStats.empty();
        }

        List<Long> sorted = values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        return PercentileStats.of(
                calculateFromSorted(sorted, 50),
                calculateFromSorted(sorted, 95),
                calculateFromSorted(sorted, 99)
        );
    }

    // ── Private helper ───────────────────────────────────────────────────────

    private static long calculateFromSorted(List<Long> sorted, int percentile) {
        if (percentile == 0)   return sorted.get(0);
        if (percentile == 100) return sorted.get(sorted.size() - 1);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(index);
    }

    // ── Result container ─────────────────────────────────────────────────────

    /**
     * Immutable container holding three response time percentiles.
     */
    public static final class PercentileStats {

        private final long p50;
        private final long p95;
        private final long p99;

        private PercentileStats(long p50, long p95, long p99) {
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }

        /**
         * Creates an instance with the given percentile values.
         */
        public static PercentileStats of(long p50, long p95, long p99) {
            return new PercentileStats(p50, p95, p99);
        }

        /**
         * Returns an empty instance (all values = 0) for the case of no data.
         */
        public static PercentileStats empty() {
            return new PercentileStats(0L, 0L, 0L);
        }

        /** @return median response time (milliseconds) */
        public long getP50() { return p50; }

        /** @return 95th percentile response time (milliseconds) */
        public long getP95() { return p95; }

        /** @return 99th percentile response time (milliseconds) */
        public long getP99() { return p99; }

        @Override
        public String toString() {
            return "PercentileStats{p50=" + p50 + "ms, p95=" + p95 + "ms, p99=" + p99 + "ms}";
        }
    }
}
