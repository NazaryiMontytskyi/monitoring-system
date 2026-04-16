package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Services", description = "Microservice registration and management")
public class ServiceController {

    private final RegisteredServiceService service;

    @PostMapping
    @Operation(summary = "Register a new microservice")
    @ApiResponse(responseCode = "201", description = "Service registered successfully")
    @ApiResponse(responseCode = "409", description = "Service with the same name is already registered")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    public ResponseEntity<ServiceResponse> register(@Valid @RequestBody ServiceRegistrationRequest request) {
        ServiceResponse response = service.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "List all registered microservices")
    @ApiResponse(responseCode = "200", description = "List of services returned")
    public ResponseEntity<List<ServiceResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get details of a registered microservice")
    @ApiResponse(responseCode = "200", description = "Service details returned")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<ServiceResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Unregister a microservice")
    @ApiResponse(responseCode = "204", description = "Service deleted")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
