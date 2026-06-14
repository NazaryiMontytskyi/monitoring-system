package com.nmontytskyi.demo.user;

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

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AtomicLong idGenerator = new AtomicLong(4);
    private final Map<Long, User> store = new ConcurrentHashMap<>(Map.of(
            1L, new User(1L, "alice.johnson", "alice.johnson@example.com", "ADMIN", LocalDateTime.of(2024, 1, 15, 9, 0)),
            2L, new User(2L, "bob.smith", "bob.smith@example.com", "USER", LocalDateTime.of(2024, 3, 22, 14, 30)),
            3L, new User(3L, "carol.white", "carol.white@example.com", "USER", LocalDateTime.of(2024, 5, 10, 11, 45))
    ));

    @GetMapping
    public List<User> getAll() throws InterruptedException {
        log.info("GET /api/users — returning all users");
        sleep(40, 120);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) throws InterruptedException {
        log.info("GET /api/users/{}", id);
        sleep(20, 70);
        User user = store.get(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User request) throws InterruptedException {
        log.info("POST /api/users — creating user username={}", request.username());
        sleep(60, 160);
        long newId = idGenerator.getAndIncrement();
        User saved = new User(newId, request.username(), request.email(), request.role(), LocalDateTime.now());
        store.put(newId, saved);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable Long id, @RequestParam String role)
            throws InterruptedException {
        log.info("PUT /api/users/{}/role — new role={}", id, role);
        sleep(30, 90);
        User existing = store.get(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        User updated = new User(existing.id(), existing.username(), existing.email(), role, existing.createdAt());
        store.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws InterruptedException {
        log.info("DELETE /api/users/{}", id);
        sleep(20, 60);
        if (!store.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }
        store.remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/slow")
    public List<User> getSlow() throws InterruptedException {
        log.info("GET /api/users/slow — simulating slow response");
        sleep(500, 900);
        return new ArrayList<>(store.values());
    }

    @GetMapping("/error")
    public List<User> getError() throws InterruptedException {
        log.info("GET /api/users/error — error simulation endpoint");
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            throw new RuntimeException("User service database connection lost");
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

    public record User(Long id, String username, String email, String role, LocalDateTime createdAt) {}
}
