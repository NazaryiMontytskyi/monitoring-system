package com.nmontytskyi.monitoring.server.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaUpdateRequest {
    private double uptimePercent;
    private long maxResponseTimeMs;
    private double maxErrorRatePercent;
    private String description;
}
