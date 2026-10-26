package com.petcommerce.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic orderCreatedEventsTopic() {
        return new NewTopic("order-created-events", 1, (short) 1);
    }

    @Bean
    NewTopic orderConfirmedEventsTopic() {
        return new NewTopic("order-confirmed-events", 1, (short) 1);
    }

    @Bean
    NewTopic petReservedEventsTopic() {
        return new NewTopic("pet-reserved-events", 1, (short) 1);
    }
}
