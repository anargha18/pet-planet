package com.petcommerce.platform.kafka;

/** Kafka record header names written by the outbox relay and read for log correlation. */
public final class EventHeaders {

    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String CORRELATION_ID = "correlationId";

    private EventHeaders() {
    }
}
