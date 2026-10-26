package com.petcommerce.catalog.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic petCatalogEventsTopic() {
        return new NewTopic("pet-catalog-events", 1, (short) 1);
    }
}
