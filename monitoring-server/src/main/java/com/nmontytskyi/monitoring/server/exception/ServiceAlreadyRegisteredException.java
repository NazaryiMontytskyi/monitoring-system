package com.nmontytskyi.monitoring.server.exception;

public class ServiceAlreadyRegisteredException extends RuntimeException {

    public ServiceAlreadyRegisteredException(String name) {
        super("Service already registered with name: " + name);
    }
}
