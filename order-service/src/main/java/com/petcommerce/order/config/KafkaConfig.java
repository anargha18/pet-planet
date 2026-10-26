package com.petcommerce.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic paymentSucceededEventsTopic() {
        return new NewTopic("payment-succeeded-events", 1, (short) 1);
    }

    @Bean
    NewTopic paymentFailedEventsTopic() {
        return new NewTopic("payment-failed-events", 1, (short) 1);
    }

    @Bean
    NewTopic orderConfirmedEventsTopic() {
        return new NewTopic("order-confirmed-events", 1, (short) 1);
    }
}
