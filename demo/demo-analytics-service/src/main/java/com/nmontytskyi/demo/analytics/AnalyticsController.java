package com.nmontytskyi.demo.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, Report> store = new ConcurrentHashMap<>(Map.of(
            1L, new Report(1L, "DAILY_SALES", "2024-06-01", 12450.75, LocalDate.of(2024, 6, 1)),
            2L, new Report(2L, "WEEKLY_REVENUE", "2024-W22", 87320.40, LocalDate.of(2024, 6, 2)),
            3L, new Report(3L, "MONTHLY_USERS", "2024-05", 1542.0, LocalDate.of(2024, 6, 1))
    ));

    @GetMapping
    public List<Report> getAll() throws InterruptedException {
        log.info("GET /api/analytics — returning all reports");
        sleep(50, 140);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/analytics/{}", id);
        sleep(30, 80);
        Report report = store.get(id);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    @PostMapping
    public ResponseEntity<Report> create(@RequestBody Report request) throws InterruptedException {
        log.info("POST /api/analytics — generating report type={}, period={}", request.reportType(), request.period());
        sleep(100, 300);
        long newId = idGenerator.getAndIncrement();
        Report saved = new Report(newId, request.reportType(), request.period(), request.value(), LocalDate.now());
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() throws InterruptedException {
        log.info("GET /api/analytics/summary — computing aggregated stats");
        sleep(80, 200);
        double totalRevenue = store.values().stream()
                .filter(r -> r.reportType().contains("REVENUE") || r.reportType().contains("SALES"))
                .mapToDouble(Report::value)
                .sum();
        double totalUsers = store.values().stream()
                .filter(r -> r.reportType().contains("USERS"))
                .mapToDouble(Report::value)
                .sum();
        return Map.of(
                "totalReports", store.size(),
                "totalRevenue", totalRevenue,
                "totalUsers", (long) totalUsers,
                "generatedAt", LocalDate.now().toString()
        );
    }

    @GetMapping("/daily")
    public List<Report> getDaily() throws InterruptedException {
        log.info("GET /api/analytics/daily — returning daily sales reports");
        sleep(40, 120);
        return store.values().stream()
                .filter(r -> r.reportType().equals("DAILY_SALES"))
                .collect(Collectors.toList());
    }

    @GetMapping("/weekly")
    public List<Report> getWeekly() throws InterruptedException {
        log.info("GET /api/analytics/weekly — returning weekly revenue reports");
        sleep(40, 120);
        return store.values().stream()
                .filter(r -> r.reportType().equals("WEEKLY_REVENUE"))
                .collect(Collectors.toList());
    }

    private void sleep(long min, long max) throws InterruptedException {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public record Report(Long id, String reportType, String period, double value, LocalDate generatedAt) {}
}
