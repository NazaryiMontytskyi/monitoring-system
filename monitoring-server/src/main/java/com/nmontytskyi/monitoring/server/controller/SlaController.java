package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.sla.SlaCalculationService;
import com.nmontytskyi.monitoring.server.sla.SlaWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing SLA compliance reports for registered microservices.
 *
 * <p>Endpoint: {@code GET /api/services/{id}/sla?window=DAY}
 *
 * <ul>
 *   <li>{@code 200 OK}  — SLA report for the requested window</li>
 *   <li>{@code 404 Not Found} — no service with the given {@code id}
 *       (handled by {@code GlobalExceptionHandler})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/services/{id}/sla")
@Validated
@RequiredArgsConstructor
public class SlaController {

    private final SlaCalculationService slaCalculationService;

    /**
     * Returns the SLA compliance report for a service over the specified window.
     *
     * @param id     service identifier
     * @param window reporting time window; defaults to {@code DAY} (last 24 hours)
     * @return {@code 200} with the {@link SlaReport}, or {@code 404} if the service
     *         does not exist (thrown by {@code SlaCalculationService} and mapped
     *         by {@code GlobalExceptionHandler})
     */
    @GetMapping
    public ResponseEntity<SlaReport> getSlaReport(
            @PathVariable Long id,
            @RequestParam(defaultValue = "DAY") SlaWindow window) {
        SlaReport report = slaCalculationService.calculate(id, window);
        return ResponseEntity.ok(report);
    }
}
