package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.dto.request.SlaUpdateRequest;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import com.nmontytskyi.monitoring.server.repository.SlaDefinitionRepository;
import com.nmontytskyi.monitoring.server.sla.SlaCalculationService;
import com.nmontytskyi.monitoring.server.sla.SlaWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/{id}/sla")
@Validated
@RequiredArgsConstructor
public class SlaController {

    private final SlaCalculationService slaCalculationService;
    private final SlaDefinitionRepository slaDefinitionRepository;
    private final RegisteredServiceRepository registeredServiceRepository;

    @GetMapping
    public ResponseEntity<SlaReport> getSlaReport(
            @PathVariable Long id,
            @RequestParam(defaultValue = "DAY") SlaWindow window) {
        SlaReport report = slaCalculationService.calculate(id, window);
        return ResponseEntity.ok(report);
    }

    @PutMapping
    public ResponseEntity<Void> updateSla(
            @PathVariable Long id,
            @RequestBody SlaUpdateRequest request) {
        if (!registeredServiceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Service not found: " + id);
        }
        slaDefinitionRepository.upsert(
                id,
                request.getUptimePercent(),
                request.getMaxResponseTimeMs(),
                request.getMaxErrorRatePercent(),
                request.getDescription());
        return ResponseEntity.ok().build();
    }
}
