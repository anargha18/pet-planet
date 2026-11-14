package com.petcommerce.notification.config;

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
    NewTopic notificationSentEventsTopic() {
        return TopicBuilder.name("notification-sent-events").partitions(1).replicas(1).build();
    }
}
