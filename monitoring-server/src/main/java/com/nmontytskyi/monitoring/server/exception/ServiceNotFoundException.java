package com.nmontytskyi.monitoring.server.exception;

public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(Long id) {
        super("Service not found with id: " + id);
    }

    public ServiceNotFoundException(String name) {
        super("Service not found with name: " + name);
    }
}
