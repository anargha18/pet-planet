package com.petcommerce.platform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Runs the outbox relay on a schedule for every {@link OutboxStore} bean a service defines (R-EVT-03).
 * A service only writes outbox rows inside its business transaction and exposes an OutboxStore; publishing is here.
 */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, TransactionAutoConfiguration.class})
@ConditionalOnBean(OutboxStore.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    public OutboxRelay outboxRelay(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${platform.outbox.batch-size:50}") int batchSize,
            @Value("${platform.outbox.send-timeout:10s}") Duration sendTimeout) {
        return new OutboxRelay(kafkaTemplate, batchSize, sendTimeout);
    }

    @Bean
    public OutboxRelayScheduler outboxRelayScheduler(
            OutboxRelay outboxRelay, List<OutboxStore> stores, PlatformTransactionManager transactionManager) {
        return new OutboxRelayScheduler(outboxRelay, stores, new TransactionTemplate(transactionManager));
    }

    /** Relays each store's pending rows in its own transaction. */
    public static class OutboxRelayScheduler {

        private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

        private final OutboxRelay relay;
        private final List<OutboxStore> stores;
        private final TransactionTemplate transactionTemplate;

        OutboxRelayScheduler(OutboxRelay relay, List<OutboxStore> stores, TransactionTemplate transactionTemplate) {
            this.relay = relay;
            this.stores = stores;
            this.transactionTemplate = transactionTemplate;
        }

        @Scheduled(fixedDelayString = "${platform.outbox.relay-interval-ms:500}")
        public void relayPending() {
            for (OutboxStore store : stores) {
                try {
                    transactionTemplate.executeWithoutResult(status -> relay.relayBatch(store));
                } catch (RuntimeException ex) {
                    log.warn("Outbox relay run failed for {}: {}", store.getClass().getSimpleName(), ex.getMessage());
                }
            }
        }
    }
}
