package com.nmontytskyi.monitoring.server.prediction;

import java.util.List;

public class LinearRegressionEngine {

    private LinearRegressionEngine() {}

    public static PredictionResult predict(List<Double> timestamps,
                                           List<Double> values,
                                           double offsetSeconds) {
        int n = timestamps.size();
        if (n < 2) {
            throw new IllegalArgumentException("At least 2 data points required");
        }

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = timestamps.get(i);
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = (n * sumX2) - (sumX * sumX);
        double slope;
        double intercept;

        if (denominator == 0) {
            slope = 0;
            intercept = sumY / n;
        } else {
            slope = ((n * sumXY) - (sumX * sumY)) / denominator;
            intercept = (sumY - slope * sumX) / n;
        }

        double lastTimestamp = timestamps.get(n - 1);
        double predictedValue = slope * (lastTimestamp + offsetSeconds) + intercept;

        double meanY = sumY / n;
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double yHat = slope * timestamps.get(i) + intercept;
            ssRes += Math.pow(values.get(i) - yHat, 2);
            ssTot += Math.pow(values.get(i) - meanY, 2);
        }

        double rSquared = (ssTot == 0) ? 1.0 : 1.0 - (ssRes / ssTot);

        return new PredictionResult(predictedValue, rSquared, slope);
    }

    public record PredictionResult(
            double predictedValue,
            double rSquared,
            double slope
    ) {}
}
