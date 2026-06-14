package com.nmontytskyi.demo.notification;

import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MonitoredMicroservice(
        name = "notification-service",
        trackAllEndpoints = true,
        bufferFlushIntervalMs = 5000
)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
