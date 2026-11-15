package com.petcommerce.platform.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;

/**
 * Kafka conventions shared by every event-driven service (R-EVT-01, R-EVT-04, R-EVT-08).
 *
 * <ul>
 *   <li><b>Wire format:</b> values are plain JSON strings (String serializer/deserializer in each service's
 *       application.yml). No Java class names travel with a message, so services never share event classes.</li>
 *   <li><b>Type mapping:</b> the JSON converter infers the target type from the {@code @KafkaListener}
 *       method parameter.</li>
 *   <li><b>Failures:</b> a failing record is retried a few times, then published to {@code <topic>.DLT}
 *       and the consumer moves on (no poison-message loops). Unreadable JSON goes to the DLT without retries.</li>
 *   <li><b>Log correlation:</b> the {@code correlationId}, {@code eventType} and {@code eventId} headers are put
 *       into the logging MDC while a record is processed.</li>
 * </ul>
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaOperations.class)
public class KafkaPlatformAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaPlatformAutoConfiguration.class);

    /** Dead-letter topic name suffix: failed records from {@code orders} go to {@code orders.DLT}. */
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    @ConditionalOnMissingBean(RecordMessageConverter.class)
    public RecordMessageConverter jsonRecordMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler deadLetterErrorHandler(
            KafkaOperations<?, ?> kafkaOperations,
            @Value("${platform.kafka.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${platform.kafka.retry.max-retries:3}") long maxRetries) {
        // Explicit destination "<topic>.DLT" (the library default differs between versions); partition -1 lets
        // Kafka choose, so a DLT does not need as many partitions as its source topic.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, ex) -> {
                    log.error("Sending record from {}-{}@{} to {} after failure: {}",
                            record.topic(), record.partition(), record.offset(), record.topic() + DLT_SUFFIX, ex.getMessage());
                    recoverer.accept(record, ex);
                },
                new FixedBackOff(retryIntervalMs, maxRetries));
        return errorHandler;
    }

    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    public RecordInterceptor<Object, Object> correlationMdcRecordInterceptor() {
        return new CorrelationMdcRecordInterceptor();
    }

    /** Copies event headers into the MDC for the duration of record processing. */
    static class CorrelationMdcRecordInterceptor implements RecordInterceptor<Object, Object> {

        @Override
        public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
            put(record, EventHeaders.CORRELATION_ID);
            put(record, EventHeaders.EVENT_TYPE);
            put(record, EventHeaders.EVENT_ID);
            return record;
        }

        @Override
        public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
            MDC.remove(EventHeaders.CORRELATION_ID);
            MDC.remove(EventHeaders.EVENT_TYPE);
            MDC.remove(EventHeaders.EVENT_ID);
        }

        private static void put(ConsumerRecord<Object, Object> record, String header) {
            Header value = record.headers().lastHeader(header);
            if (value != null) {
                MDC.put(header, new String(value.value(), StandardCharsets.UTF_8));
            }
        }
    }
}
