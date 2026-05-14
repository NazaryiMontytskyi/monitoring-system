package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller providing API access to application runtime settings.
 *
 * <p>Exposes endpoints for reading all settings and for updating the email notification
 * address. All settings are persisted in the database via
 * {@link com.nmontytskyi.monitoring.server.service.AppSettingsService} and take effect
 * immediately without a server restart.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.service.AppSettingsService
 */
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
