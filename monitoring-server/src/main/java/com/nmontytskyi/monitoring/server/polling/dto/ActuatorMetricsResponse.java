package com.nmontytskyi.monitoring.server.polling.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActuatorMetricsResponse {

    private String name;
    private List<Measurement> measurements;

    /** Returns the value of the first measurement (used for simple gauge/counter metrics). */
    public double getValue() {
        if (measurements == null || measurements.isEmpty()) return 0.0;
        return measurements.get(0).getValue();
    }

    /**
     * Returns the value of the measurement whose {@code statistic} field matches the given name
     * (case-insensitive). Used for Timer metrics where COUNT / TOTAL_TIME / MAX are separate
     * entries in the measurements array — not URL tag-filter values.
     */
    public Optional<Double> getStatisticValue(String statisticName) {
        if (measurements == null) return Optional.empty();
        return measurements.stream()
                .filter(m -> statisticName.equalsIgnoreCase(m.getStatistic()))
                .map(Measurement::getValue)
                .findFirst();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Measurement {
        private String statistic;
        private double value;
    }
}
