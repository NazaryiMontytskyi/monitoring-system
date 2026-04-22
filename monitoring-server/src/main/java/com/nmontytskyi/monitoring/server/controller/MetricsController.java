package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Metrics ingestion and retrieval")
public class MetricsController {

    private final MetricsPersistenceService service;

    @PostMapping("/endpoint")
    @Operation(summary = "Push a metric snapshot from a monitored service")
    @ApiResponse(responseCode = "202", description = "Snapshot accepted and persisted")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<MetricRecordResponse> pushSnapshot(@Valid @RequestBody MetricSnapshotRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.saveEndpointSnapshot(request));
    }

    @PostMapping("/batch")
    @Operation(summary = "Push a batch of metric snapshots (used by the starter's MetricsBuffer)")
    @ApiResponse(responseCode = "202", description = "All snapshots accepted and persisted")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    public ResponseEntity<List<MetricRecordResponse>> pushBatch(
            @Valid @RequestBody List<@Valid MetricSnapshotRequest> batch) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.saveBatch(batch));
    }

    @GetMapping("/{serviceId}/latest")
    @Operation(summary = "Get the latest metric record for a service")
    @ApiResponse(responseCode = "200", description = "Latest metric returned")
    @ApiResponse(responseCode = "404", description = "Service not found or no metrics recorded yet")
    public ResponseEntity<MetricRecordResponse> getLatest(@PathVariable Long serviceId) {
        return service.getLatest(serviceId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));
    }

    @GetMapping("/{serviceId}/aggregate")
    @Operation(summary = "Get aggregated metrics for a service over a time window")
    @ApiResponse(responseCode = "200", description = "Aggregate metrics returned")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<AggregateMetricsResponse> getAggregate(
            @PathVariable Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(service.getAggregate(serviceId, from, to));
    }
}
