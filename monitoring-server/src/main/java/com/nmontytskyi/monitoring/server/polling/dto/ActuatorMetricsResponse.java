package com.nmontytskyi.monitoring.server.polling.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActuatorMetricsResponse {

    private String name;
    private List<Measurement> measurements;

    public double getValue() {
        if (measurements == null || measurements.isEmpty()) return 0.0;
        return measurements.get(0).getValue();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Measurement {
        private String statistic;
        private double value;
    }
}
