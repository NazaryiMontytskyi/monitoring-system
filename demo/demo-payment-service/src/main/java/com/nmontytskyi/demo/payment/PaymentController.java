package com.nmontytskyi.demo.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, Payment> store = new ConcurrentHashMap<>(Map.of(
            1L, new Payment(1L, 1L, "CREDIT_CARD", 1299.99, "COMPLETED", "TXN-001-2024"),
            2L, new Payment(2L, 2L, "PAYPAL", 79.99, "PENDING", "TXN-002-2024"),
            3L, new Payment(3L, 3L, "DEBIT_CARD", 129.99, "COMPLETED", "TXN-003-2024")
    ));

    @GetMapping
    public List<Payment> getAll() throws InterruptedException {
        log.info("GET /api/payments — returning all payments");
        sleep(40, 120);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/payments/{}", id);
        sleep(20, 60);
        Payment payment = store.get(id);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(payment);
    }

    @PostMapping
    public ResponseEntity<Payment> create(@RequestBody Payment request) throws InterruptedException {
        log.info("POST /api/payments — processing payment for orderId={}, method={}", request.orderId(), request.method());
        sleep(100, 300);
        long newId = idGenerator.getAndIncrement();
        String transactionId = "TXN-" + newId + "-" + Year.now().getValue();
        Payment saved = new Payment(newId, request.orderId(), request.method(), request.amount(), "PENDING", transactionId);
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/order/{orderId}")
    public List<Payment> getByOrderId(@PathVariable Long orderId) throws InterruptedException {
        log.info("GET /api/payments/order/{}", orderId);
        sleep(30, 80);
        return store.values().stream()
                .filter(p -> p.orderId().equals(orderId))
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Payment> complete(@PathVariable Long id) throws InterruptedException {
        log.info("PUT /api/payments/{}/complete", id);
        sleep(50, 150);
        Payment existing = store.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Payment updated = new Payment(existing.id(), existing.orderId(), existing.method(),
                existing.amount(), "COMPLETED", existing.transactionId());
        store.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/slow")
    public List<Payment> getSlow() throws InterruptedException {
        log.info("GET /api/payments/slow — simulating slow payment gateway");
        sleep(600, 1000);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/error")
    public List<Payment> getError() throws InterruptedException {
        log.info("GET /api/payments/error — error simulation endpoint");
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new RuntimeException("Payment gateway timeout");
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

    public record Payment(Long id, Long orderId, String method, double amount, String status, String transactionId) {}
}
