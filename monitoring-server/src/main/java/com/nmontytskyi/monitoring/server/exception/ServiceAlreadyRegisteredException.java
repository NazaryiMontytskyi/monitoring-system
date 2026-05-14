package com.nmontytskyi.monitoring.server.exception;

/**
 * Thrown when a service registration request conflicts with an already-registered service
 * of the same name. Results in an HTTP 409 response via
 * {@link com.nmontytskyi.monitoring.server.exception.GlobalExceptionHandler}.
 */
public class ServiceAlreadyRegisteredException extends RuntimeException {

    public ServiceAlreadyRegisteredException(String name) {
        super("Service already registered with name: " + name);
    }
}
