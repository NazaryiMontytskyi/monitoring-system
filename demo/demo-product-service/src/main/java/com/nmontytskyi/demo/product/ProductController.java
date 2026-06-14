package com.nmontytskyi.demo.product;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, Product> store = new ConcurrentHashMap<>(Map.of(
            1L, new Product(1L, "Mechanical Keyboard Pro", "Electronics", 129.99, 85, "SKU-KB-001"),
            2L, new Product(2L, "Wireless Mouse X500", "Electronics", 49.99, 200, "SKU-MS-002"),
            3L, new Product(3L, "27-inch 4K Monitor", "Electronics", 599.99, 30, "SKU-MON-003")
    ));

    @GetMapping
    public List<Product> getAll() throws InterruptedException {
        log.info("GET /api/products — returning all products");
        sleep(30, 100);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/products/{}", id);
        sleep(20, 60);
        Product product = store.get(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product request) throws InterruptedException {
        log.info("POST /api/products — creating product name={}, category={}", request.name(), request.category());
        sleep(60, 180);
        long newId = idGenerator.getAndIncrement();
        String sku = "SKU-" + request.category().toUpperCase().substring(0, Math.min(3, request.category().length())) + "-" + String.format("%03d", newId);
        Product saved = new Product(newId, request.name(), request.category(), request.price(), request.stockQuantity(), sku);
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<Product> updateStock(@PathVariable Long id, @RequestParam int quantity)
            throws InterruptedException {
        log.info("PUT /api/products/{}/stock — new quantity={}", id, quantity);
        sleep(30, 80);
        Product existing = store.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        Product updated = new Product(existing.id(), existing.name(), existing.category(),
                existing.price(), quantity, existing.sku());
        store.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/category/{category}")
    public List<Product> getByCategory(@PathVariable String category) throws InterruptedException {
        log.info("GET /api/products/category/{}", category);
        sleep(30, 80);
        return store.values().stream()
                .filter(p -> p.category().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    @GetMapping("/slow")
    public List<Product> getSlow() throws InterruptedException {
        log.info("GET /api/products/slow — simulating slow catalog search");
        sleep(500, 900);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/error")
    public List<Product> getError() throws InterruptedException {
        log.info("GET /api/products/error — error simulation endpoint");
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new RuntimeException("Product catalog service temporarily unavailable");
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

    public record Product(Long id, String name, String category, double price, int stockQuantity, String sku) {}
}
