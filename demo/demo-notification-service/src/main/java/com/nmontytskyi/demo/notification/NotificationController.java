package com.nmontytskyi.demo.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, Notification> store = new ConcurrentHashMap<>(Map.of(
            1L, new Notification(1L, "alice.johnson@example.com", "EMAIL", "Your order #1 has been shipped!", "SENT", LocalDateTime.of(2024, 6, 1, 10, 15)),
            2L, new Notification(2L, "+380991234567", "SMS", "Payment of $79.99 confirmed for order #2.", "SENT", LocalDateTime.of(2024, 6, 2, 14, 0)),
            3L, new Notification(3L, "carol.white@example.com", "EMAIL", "Your account password was changed.", "PENDING", null)
    ));

    @GetMapping
    public List<Notification> getAll() throws InterruptedException {
        log.info("GET /api/notifications — returning all notifications");
        sleep(30, 100);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notification> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/notifications/{}", id);
        sleep(20, 60);
        Notification notification = store.get(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(notification);
    }

    @PostMapping
    public ResponseEntity<Notification> send(@RequestBody Notification request) throws InterruptedException {
        log.info("POST /api/notifications — sending {} to {}", request.type(), request.recipientEmail());
        sleep(80, 250);
        long newId = idGenerator.getAndIncrement();
        Notification saved = new Notification(newId, request.recipientEmail(), request.type(),
                request.message(), "PENDING", null);
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Notification> updateStatus(@PathVariable Long id, @RequestParam String status)
            throws InterruptedException {
        log.info("PUT /api/notifications/{}/status — new status={}", id, status);
        sleep(30, 80);
        Notification existing = store.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        LocalDateTime sentAt = "SENT".equals(status) ? LocalDateTime.now() : existing.sentAt();
        Notification updated = new Notification(existing.id(), existing.recipientEmail(),
                existing.type(), existing.message(), status, sentAt);
        store.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/recipient/{email}")
    public List<Notification> getByRecipient(@PathVariable String email) throws InterruptedException {
        log.info("GET /api/notifications/recipient/{}", email);
        sleep(40, 100);
        return store.values().stream()
                .filter(n -> n.recipientEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }

    @GetMapping("/slow")
    public List<Notification> getSlow() throws InterruptedException {
        log.info("GET /api/notifications/slow — simulating slow notification dispatch");
        sleep(500, 900);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/error")
    public List<Notification> getError() throws InterruptedException {
        log.info("GET /api/notifications/error — error simulation endpoint");
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new RuntimeException("Notification provider SMTP connection refused");
        }
        return new ArrayList<>(store.values());
    }

    private void sleep(long min, long max) throws InterruptedException {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public record Notification(Long id, String recipientEmail, String type, String message,
                               String status, LocalDateTime sentAt) {}
}
