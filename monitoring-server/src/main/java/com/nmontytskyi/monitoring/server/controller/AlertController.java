package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.dto.request.AlertRuleRequest;
import com.nmontytskyi.monitoring.server.dto.response.AlertEventResponse;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.service.AlertEventService;
import com.nmontytskyi.monitoring.server.service.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert rules and events management")
public class AlertController {

    private final AlertRuleService alertRuleService;
    private final AlertEventService alertEventService;

    @PostMapping("/rules")
    @Operation(summary = "Create an alert rule for a service")
    @ApiResponse(responseCode = "201", description = "Alert rule created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody AlertRuleRequest request) {
        AlertRuleResponse response = alertRuleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/rules")
    @Operation(summary = "List alert rules, optionally filtered by service")
    @ApiResponse(responseCode = "200", description = "List of alert rules")
    public ResponseEntity<List<AlertRuleResponse>> getRules(
            @RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(alertRuleService.findByServiceId(serviceId));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete an alert rule")
    @ApiResponse(responseCode = "204", description = "Alert rule deleted")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        alertRuleService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    @Operation(summary = "Get alert events for a service (paginated)")
    @ApiResponse(responseCode = "200", description = "Page of alert events")
    public ResponseEntity<Page<AlertEventResponse>> getEvents(
            @RequestParam Long serviceId,
            Pageable pageable) {
        return ResponseEntity.ok(alertEventService.findByServiceId(serviceId, pageable));
    }
}
