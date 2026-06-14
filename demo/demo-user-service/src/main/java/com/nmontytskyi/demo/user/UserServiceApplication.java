package com.nmontytskyi.demo.user;

import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MonitoredMicroservice(
        name = "user-service",
        trackAllEndpoints = true,
        bufferFlushIntervalMs = 5000
)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
