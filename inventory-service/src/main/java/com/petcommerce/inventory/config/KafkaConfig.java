package com.petcommerce.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics this service PRODUCES (R-EVT-06): the producer owns and declares its topics; consumers only subscribe.
 * Failed records go to "&lt;topic&gt;.DLT", created on first use.
 */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic petReservedEventsTopic() {
        return TopicBuilder.name("pet-reserved-events").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic petReservationFailedEventsTopic() {
        return TopicBuilder.name("pet-reservation-failed-events").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic petReservationExpiredEventsTopic() {
        return TopicBuilder.name("pet-reservation-expired-events").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic petsSoldEventsTopic() {
        return TopicBuilder.name("pets-sold-events").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic inventorySaleConflictEventsTopic() {
        return TopicBuilder.name("inventory-sale-conflict-events").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic inventoryStatusEventsTopic() {
        return TopicBuilder.name("inventory-status-events").partitions(1).replicas(1).build();
    }
}
