package com.nmontytskyi.demo.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, Order> store = new ConcurrentHashMap<>(Map.of(
            1L, new Order(1L, "Alice Johnson", List.of("Laptop", "Mouse"), "DELIVERED", 1299.99),
            2L, new Order(2L, "Bob Smith", List.of("Keyboard"), "PROCESSING", 79.99),
            3L, new Order(3L, "Carol White", List.of("Mouse", "Keyboard"), "SHIPPED", 129.99)
    ));

    @GetMapping
    public List<Order> getAll() throws InterruptedException {
        log.info("GET /api/orders — returning all orders");
        sleep(50, 150);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/orders/{}", id);
        sleep(30, 80);
        Order order = store.get(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody Order request) throws InterruptedException {
        log.info("POST /api/orders — creating order for customer={}", request.customerName());
        sleep(80, 200);
        long newId = idGenerator.getAndIncrement();
        Order saved = new Order(newId, request.customerName(), request.items(), "PENDING", request.totalAmount());
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(@PathVariable Long id, @RequestParam String status)
            throws InterruptedException {
        log.info("PUT /api/orders/{}/status — new status={}", id, status);
        sleep(40, 100);
        Order existing = store.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Order updated = new Order(existing.id(), existing.customerName(), existing.items(), status, existing.totalAmount());
        store.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/slow")
    public List<Order> getSlow() throws InterruptedException {
        log.info("GET /api/orders/slow — simulating slow response");
        sleep(500, 900);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/error")
    public List<Order> getError() throws InterruptedException {
        log.info("GET /api/orders/error — error simulation endpoint");
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new RuntimeException("Order processing failed");
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

    public record Order(Long id, String customerName, List<String> items, String status, double totalAmount) {}
}
