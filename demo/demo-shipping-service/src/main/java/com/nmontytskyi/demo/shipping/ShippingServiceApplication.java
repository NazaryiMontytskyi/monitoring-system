package com.nmontytskyi.demo.shipping;

import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MonitoredMicroservice(
        name = "shipping-service",
        trackAllEndpoints = true,
        bufferFlushIntervalMs = 5000
)
public class ShippingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
