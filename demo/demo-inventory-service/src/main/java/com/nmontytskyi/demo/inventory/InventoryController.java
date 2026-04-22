package com.nmontytskyi.demo.inventory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Map<Long, Item> store = new ConcurrentHashMap<>(Map.of(
            1L, new Item(1L, "Laptop", 10),
            2L, new Item(2L, "Mouse", 50),
            3L, new Item(3L, "Keyboard", 30)
    ));

    @GetMapping
    public List<Item> getAll() {
        log.info("GET /api/inventory — returning {} items", store.size());
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> getById(@PathVariable Long id) {
        log.info("GET /api/inventory/{}", id);
        Item item = store.get(id);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    @PostMapping
    public ResponseEntity<Item> create(@RequestBody Item item) {
        long newId = idGenerator.incrementAndGet();
        Item saved = new Item(newId, item.name(), item.quantity());
        store.put(newId, saved);
        log.info("POST /api/inventory — created item id={}", newId);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/slow")
    public List<Item> getSlow() throws InterruptedException {
        log.info("GET /api/inventory/slow — simulating slow response");
        Thread.sleep(500);
        return new ArrayList<>(store.values());
    }

    public record Item(Long id, String name, int quantity) {
    }
}
