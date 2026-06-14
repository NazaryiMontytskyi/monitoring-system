package com.nmontytskyi.demo.product;

import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MonitoredMicroservice(
        name = "product-service",
        trackAllEndpoints = true,
        bufferFlushIntervalMs = 5000
)
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
