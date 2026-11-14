package com.petcommerce.payment.config;

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
    NewTopic paymentSucceededEventsTopic() {
        return TopicBuilder.name("payment-succeeded-events").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentFailedEventsTopic() {
        return TopicBuilder.name("payment-failed-events").partitions(1).replicas(1).build();
    }
}
