package com.nmontytskyi.monitoring.detector;

import java.util.List;

/**
 * Statistical anomaly detector based on the Z-score method.
 *
 * <p>Determines whether a current metric value is anomalous relative to
 * its own historical norm. Unlike hard thresholds (e.g. {@code response_time > 1000ms}),
 * this approach adapts to the actual norm of each service:
 *
 * <ul>
 *   <li>Service A with a baseline of 800ms → anomaly above ~1200ms</li>
 *   <li>Service B with a baseline of 50ms  → anomaly above ~130ms</li>
 * </ul>
 *
 * <h3>Algorithm (Z-score):</h3>
 * <pre>
 *   μ (mean)               = sum(values) / n
 *   σ (standard deviation) = sqrt(sum((xi - μ)²) / n)
 *   Z                      = (currentValue - μ) / σ
 * </pre>
 *
 * <p>A value is considered anomalous when {@code |Z| > threshold}.
 * The standard threshold is {@code 3.0} (three-sigma rule):
 * under a normal distribution only 0.3% of values fall outside this range.
 *
 * <p>At least {@value #MIN_SAMPLE_SIZE} historical measurements are required
 * for a reliable calculation. When there is insufficient data,
 * {@link AnomalyResult#insufficient()} is returned.
 */
public class AnomalyDetector {

    /**
     * Default Z-score threshold (three-sigma rule).
     */
    public static final double DEFAULT_THRESHOLD = 3.0;

    /**
     * Minimum number of historical values required for a reliable calculation.
     */
    public static final int MIN_SAMPLE_SIZE = 10;

    private final double threshold;

    /**
     * Creates a detector with the default threshold of {@value #DEFAULT_THRESHOLD}.
     */
    public AnomalyDetector() {
        this.threshold = DEFAULT_THRESHOLD;
    }

    /**
     * Creates a detector with a custom Z-score threshold.
     *
     * @param threshold deviation threshold; recommended values: 2.0 (sensitive), 3.0 (standard)
     * @throws IllegalArgumentException if the threshold is not positive
     */
    public AnomalyDetector(double threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Threshold must be positive, got: " + threshold);
        }
        this.threshold = threshold;
    }

    /**
     * Analyses the current value against a set of historical measurements.
     *
     * @param currentValue     current metric value (e.g. response time in ms)
     * @param historicalValues list of previous measurements of the same metric;
     *                         must not include {@code currentValue}
     * @return analysis result containing the Z-score and anomaly flag
     */
    public AnomalyResult analyze(double currentValue, List<Double> historicalValues) {
        if (historicalValues == null || historicalValues.size() < MIN_SAMPLE_SIZE) {
            return AnomalyResult.insufficient();
        }

        double mean   = calculateMean(historicalValues);
        double stdDev = calculateStdDev(historicalValues, mean);

        if (stdDev == 0.0) {
            // All values are identical — current value is either exactly the norm or an absolute anomaly
            boolean isAnomaly = currentValue != mean;
            return AnomalyResult.of(isAnomaly ? Double.MAX_VALUE : 0.0, isAnomaly);
        }

        double zScore = (currentValue - mean) / stdDev;
        boolean isAnomaly = Math.abs(zScore) > threshold;

        return AnomalyResult.of(zScore, isAnomaly);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private double calculateMean(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double calculateStdDev(List<Double> values, double mean) {
        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    // ── Analysis result ──────────────────────────────────────────────────────

    /**
     * Immutable result of an anomaly analysis for a single measurement.
     */
    public static final class AnomalyResult {

        private final double  zScore;
        private final boolean anomaly;
        private final boolean sufficientData;

        private AnomalyResult(double zScore, boolean anomaly, boolean sufficientData) {
            this.zScore         = zScore;
            this.anomaly        = anomaly;
            this.sufficientData = sufficientData;
        }

        /**
         * Creates a result with sufficient data available.
         *
         * @param zScore  computed Z-score value
         * @param anomaly {@code true} if the value is anomalous
         */
        public static AnomalyResult of(double zScore, boolean anomaly) {
            return new AnomalyResult(zScore, anomaly, true);
        }

        /**
         * Returns a result indicating insufficient data for analysis.
         * {@code anomaly} will be {@code false}, {@code zScore} will be {@code 0.0}.
         */
        public static AnomalyResult insufficient() {
            return new AnomalyResult(0.0, false, false);
        }

        /**
         * @return Z-score of the current value; {@code 0.0} when data is insufficient
         */
        public double getZScore() {
            return zScore;
        }

        /**
         * @return {@code true} if the value is statistically anomalous
         */
        public boolean isAnomaly() {
            return anomaly;
        }

        /**
         * @return {@code true} if there was enough data to perform the calculation
         */
        public boolean hasSufficientData() {
            return sufficientData;
        }

        @Override
        public String toString() {
            return "AnomalyResult{zScore=" + zScore
                    + ", anomaly=" + anomaly
                    + ", sufficientData=" + sufficientData + "}";
        }
    }
}
