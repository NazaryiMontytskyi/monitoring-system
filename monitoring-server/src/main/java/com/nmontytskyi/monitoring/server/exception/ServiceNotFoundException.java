package com.nmontytskyi.monitoring.server.exception;

/**
 * Thrown when a requested service ID does not correspond to any registered service.
 * Results in an HTTP 404 response via {@link com.nmontytskyi.monitoring.server.exception.GlobalExceptionHandler}.
 */
public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(Long id) {
        super("Service not found with id: " + id);
    }

    public ServiceNotFoundException(String name) {
        super("Service not found with name: " + name);
    }
}
