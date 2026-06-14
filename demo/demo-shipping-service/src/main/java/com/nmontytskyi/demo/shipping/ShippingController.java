package com.nmontytskyi.demo.shipping;

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
@RequestMapping("/api/shipments")
public class ShippingController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, Shipment> store = new ConcurrentHashMap<>(Map.of(
            1L, new Shipment(1L, 1L, "DHL", "DHL-928374651", "DELIVERED", LocalDate.of(2024, 6, 5)),
            2L, new Shipment(2L, 2L, "FedEx", "FX-11029384756", "IN_TRANSIT", LocalDate.of(2024, 6, 10)),
            3L, new Shipment(3L, 3L, "UPS", "UPS-556473829", "PROCESSING", LocalDate.of(2024, 6, 12))
    ));

    @GetMapping
    public List<Shipment> getAll() throws InterruptedException {
        log.info("GET /api/shipments — returning all shipments");
        sleep(40, 130);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Shipment> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/shipments/{}", id);
        sleep(20, 70);
        Shipment shipment = store.get(id);
        if (shipment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(shipment);
    }

    @PostMapping
    public ResponseEntity<Shipment> create(@RequestBody Shipment request) throws InterruptedException {
        log.info("POST /api/shipments — creating shipment for orderId={}, carrier={}", request.orderId(), request.carrier());
        sleep(70, 200);
        long newId = idGenerator.getAndIncrement();
        String trackingNumber = request.carrier().toUpperCase().substring(0, 3) + "-" + (900000000L + newId);
        Shipment saved = new Shipment(newId, request.orderId(), request.carrier(),
                trackingNumber, "PROCESSING", request.estimatedDelivery());
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Shipment> updateStatus(@PathVariable Long id, @RequestParam String status)
            throws InterruptedException {
        log.info("PUT /api/shipments/{}/status — new status={}", id, status);
        sleep(30, 90);
        Shipment existing = store.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Shipment updated = new Shipment(existing.id(), existing.orderId(), existing.carrier(),
                existing.trackingNumber(), status, existing.estimatedDelivery());
        store.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/order/{orderId}")
    public List<Shipment> getByOrderId(@PathVariable Long orderId) throws InterruptedException {
        log.info("GET /api/shipments/order/{}", orderId);
        sleep(30, 80);
        return store.values().stream()
                .filter(s -> s.orderId().equals(orderId))
                .collect(Collectors.toList());
    }

    @GetMapping("/slow")
    public List<Shipment> getSlow() throws InterruptedException {
        log.info("GET /api/shipments/slow — simulating slow carrier API response");
        sleep(500, 900);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/error")
    public List<Shipment> getError() throws InterruptedException {
        log.info("GET /api/shipments/error — error simulation endpoint");
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new RuntimeException("Carrier API rate limit exceeded");
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

    public record Shipment(Long id, Long orderId, String carrier, String trackingNumber,
                           String status, LocalDate estimatedDelivery) {}
}
