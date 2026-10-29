package com.petcommerce.delivery.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic shipmentCreatedEventsTopic() {
        return new NewTopic("shipment-created-events", 1, (short) 1);
    }

    @Bean
    NewTopic deliveryStatusUpdatedEventsTopic() {
        return new NewTopic("delivery-status-updated-events", 1, (short) 1);
    }
}
