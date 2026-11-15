package com.petcommerce.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Helpers for building outbox rows from event objects. */
public final class OutboxPayloads {

    private OutboxPayloads() {
    }

    /** Serializes an event to the JSON that will be stored in the outbox and sent to Kafka unchanged. */
    public static String toJson(ObjectMapper objectMapper, Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize event " + event.getClass().getSimpleName(), ex);
        }
    }

    /** Reads a UUID field (e.g. "eventId", "correlationId") from event JSON; null when absent or not a UUID. */
    public static UUID uuidField(ObjectMapper objectMapper, String json, String field) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode node = root == null ? null : root.get(field);
            return node == null || node.isNull() ? null : UUID.fromString(node.asText());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** The event's own eventId, so the outbox row, the Kafka header and the payload all agree. */
    public static UUID eventIdOrRandom(ObjectMapper objectMapper, String json) {
        UUID eventId = uuidField(objectMapper, json, "eventId");
        return eventId != null ? eventId : UUID.randomUUID();
    }
}
