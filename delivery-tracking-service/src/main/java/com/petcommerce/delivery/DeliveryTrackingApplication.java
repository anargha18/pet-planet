package com.petcommerce.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeliveryTrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryTrackingApplication.class, args);
    }
}
