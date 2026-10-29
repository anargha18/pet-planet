package com.petcommerce.order.config;

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
    NewTopic orderCancelledEventsTopic() {
        return new NewTopic("order-cancelled-events", 1, (short) 1);
    }

    @Bean
    NewTopic petReservedEventsTopic() {
        return new NewTopic("pet-reserved-events", 1, (short) 1);
    }

    @Bean
    NewTopic petReservationFailedEventsTopic() {
        return new NewTopic("pet-reservation-failed-events", 1, (short) 1);
    }

    @Bean
    NewTopic petReservationExpiredEventsTopic() {
        return new NewTopic("pet-reservation-expired-events", 1, (short) 1);
    }

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
