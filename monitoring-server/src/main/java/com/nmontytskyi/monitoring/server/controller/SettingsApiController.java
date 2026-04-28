package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsApiController {

    private final AppSettingsService appSettingsService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(appSettingsService.getAll());
    }

    @PostMapping("/email")
    public ResponseEntity<Void> updateEmail(@RequestBody Map<String, String> body) {
        String emailTo = body.get("emailTo");
        if (emailTo == null || emailTo.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        appSettingsService.set("notification.email.to", emailTo.trim());
        return ResponseEntity.ok().build();
    }
}
