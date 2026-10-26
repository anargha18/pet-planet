package com.petcommerce.fulfillment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic deliveryStatusUpdatedEventsTopic() {
        return new NewTopic("delivery-status-updated-events", 1, (short) 1);
    }

    @Bean
    NewTopic fulfillmentUpdatedEventsTopic() {
        return new NewTopic("fulfillment-updated-events", 1, (short) 1);
    }
}
