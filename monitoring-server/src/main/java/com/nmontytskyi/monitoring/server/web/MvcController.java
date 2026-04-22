package com.nmontytskyi.monitoring.server.web;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.dto.request.AlertRuleRequest;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.AlertEventResponse;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.dto.response.DashboardSummaryResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.service.AlertEventService;
import com.nmontytskyi.monitoring.server.service.AlertRuleService;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import com.nmontytskyi.monitoring.server.sla.SlaCalculationService;
import com.nmontytskyi.monitoring.server.sla.SlaWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequiredArgsConstructor
public class MvcController {

    private final RegisteredServiceService serviceService;
    private final MetricsPersistenceService metricsService;
    private final SlaCalculationService slaService;
    private final AlertRuleService alertRuleService;
    private final AlertEventService alertEventService;

    @GetMapping("/")
    public String dashboard(Model model) {
        List<ServiceResponse> services = serviceService.findAll();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(24);

        List<DashboardSummaryResponse.ServiceSummary> summaries = services.stream()
                .map(s -> buildServiceSummary(s, from, to))
                .toList();

        Map<HealthStatus, Long> counts = services.stream()
                .collect(Collectors.groupingBy(ServiceResponse::getStatus, Collectors.counting()));

        DashboardSummaryResponse summary = DashboardSummaryResponse.builder()
                .services(summaries)
                .totalServices(services.size())
                .healthyCount(counts.getOrDefault(HealthStatus.UP, 0L))
                .degradedCount(counts.getOrDefault(HealthStatus.DEGRADED, 0L))
                .downCount(counts.getOrDefault(HealthStatus.DOWN, 0L))
                .build();

        List<AlertEventResponse> recentEvents = services.stream()
                .flatMap(s -> fetchEventsQuietly(s.getId()))
                .sorted(Comparator.comparing(AlertEventResponse::getFiredAt).reversed())
                .limit(5)
                .toList();

        List<String> chartLabels = summaries.stream().map(DashboardSummaryResponse.ServiceSummary::getName).toList();
        List<Double> chartValues = summaries.stream()
                .map(s -> s.getAvgResponseTimeMs() != null ? s.getAvgResponseTimeMs() : 0.0)
                .toList();
        List<String> chartStatuses = summaries.stream()
                .map(s -> s.getStatus() != null ? s.getStatus().name() : "UNKNOWN")
                .toList();

        model.addAttribute("summary", summary);
        model.addAttribute("recentEvents", recentEvents);
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartValues", chartValues);
        model.addAttribute("chartStatuses", chartStatuses);
        model.addAttribute("currentPath", "/");
        return "dashboard";
    }

    @GetMapping("/services/{id}")
    public String serviceDetail(@PathVariable Long id, Model model) {
        ServiceResponse service = serviceService.findById(id);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(24);

        AggregateMetricsResponse aggregate;
        try {
            aggregate = metricsService.getAggregate(id, from, to);
        } catch (Exception e) {
            aggregate = emptyAggregate();
        }

        SlaReport sla = slaService.calculate(id, SlaWindow.DAY);

        model.addAttribute("service", service);
        model.addAttribute("aggregate", aggregate);
        model.addAttribute("sla", sla);
        model.addAttribute("currentPath", "/services/" + id);
        return "service-detail";
    }

    @GetMapping("/services/{id}/sla")
    public String slaReport(@PathVariable Long id,
                            @RequestParam(defaultValue = "DAY") SlaWindow window,
                            Model model) {
        ServiceResponse service = serviceService.findById(id);
        SlaReport slaReport = slaService.calculate(id, window);

        model.addAttribute("service", service);
        model.addAttribute("slaReport", slaReport);
        model.addAttribute("window", window);
        model.addAttribute("windows", SlaWindow.values());
        model.addAttribute("currentPath", "/services/" + id + "/sla");
        return "sla-report";
    }

    @GetMapping("/alerts")
    public String alerts(Model model) {
        List<ServiceResponse> allServices = serviceService.findAll();
        List<AlertRuleResponse> rules = alertRuleService.findByServiceId(null);

        List<AlertEventResponse> recentEvents = allServices.stream()
                .flatMap(s -> fetchEventsQuietly(s.getId()))
                .sorted(Comparator.comparing(AlertEventResponse::getFiredAt).reversed())
                .limit(20)
                .toList();

        Map<Long, String> serviceNames = allServices.stream()
                .collect(Collectors.toMap(ServiceResponse::getId, ServiceResponse::getName));

        model.addAttribute("services", allServices);
        model.addAttribute("rules", rules);
        model.addAttribute("events", recentEvents);
        model.addAttribute("serviceNames", serviceNames);
        model.addAttribute("currentPath", "/alerts");
        return "alerts";
    }

    @PostMapping("/alerts/rules")
    public String createAlertRule(
            @RequestParam Long serviceId,
            @RequestParam String metricType,
            @RequestParam String comparator,
            @RequestParam Double threshold,
            @RequestParam(defaultValue = "15") int cooldownMinutes,
            @RequestParam(required = false, defaultValue = "false") boolean enabled,
            RedirectAttributes redirectAttrs) {
        AlertRuleRequest request = AlertRuleRequest.builder()
                .serviceId(serviceId)
                .metricType(AlertRuleEntity.MetricType.valueOf(metricType))
                .comparator(AlertRuleEntity.Comparator.valueOf(comparator))
                .threshold(threshold)
                .enabled(enabled)
                .cooldownMinutes(cooldownMinutes)
                .build();
        alertRuleService.create(request);
        redirectAttrs.addFlashAttribute("successMessage", "Rule created successfully");
        return "redirect:/alerts";
    }

    @PostMapping("/alerts/rules/{id}/delete")
    public String deleteAlertRule(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        alertRuleService.deleteById(id);
        redirectAttrs.addFlashAttribute("successMessage", "Rule deleted successfully");
        return "redirect:/alerts";
    }

    @ExceptionHandler(ServiceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound() {
        return "errors/404";
    }

    private DashboardSummaryResponse.ServiceSummary buildServiceSummary(
            ServiceResponse svc, LocalDateTime from, LocalDateTime to) {
        Double avgMs = null;
        Double uptimePct = null;
        Double cpuUsage = null;

        try {
            AggregateMetricsResponse agg = metricsService.getAggregate(svc.getId(), from, to);
            if (agg.getTotalRequests() > 0) {
                avgMs = agg.getAvgResponseTimeMs();
                uptimePct = agg.getUptimePercent();
            }
        } catch (Exception ignored) {}

        try {
            cpuUsage = metricsService.getLatest(svc.getId())
                    .map(MetricRecordResponse::getCpuUsage)
                    .orElse(null);
        } catch (Exception ignored) {}

        return DashboardSummaryResponse.ServiceSummary.builder()
                .id(svc.getId())
                .name(svc.getName())
                .status(svc.getStatus())
                .avgResponseTimeMs(avgMs)
                .uptimePercent(uptimePct)
                .cpuUsage(cpuUsage)
                .build();
    }

    private Stream<AlertEventResponse> fetchEventsQuietly(Long serviceId) {
        try {
            return alertEventService.findByServiceId(serviceId, PageRequest.of(0, 20)).stream();
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    private AggregateMetricsResponse emptyAggregate() {
        return AggregateMetricsResponse.builder()
                .avgResponseTimeMs(0)
                .minResponseTimeMs(0)
                .maxResponseTimeMs(0)
                .totalRequests(0)
                .uptimePercent(0)
                .build();
    }
}
