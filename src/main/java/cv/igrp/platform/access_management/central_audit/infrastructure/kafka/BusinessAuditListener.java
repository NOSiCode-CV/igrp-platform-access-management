package cv.igrp.platform.access_management.central_audit.infrastructure.kafka;

import cv.igrp.platform.access_management.central_audit.domain.service.BusinessAuditStore;
import cv.igrp.platform.access_management.central_audit.domain.service.BusinessAuditStore.InvalidEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumes {@code igrp.audit} into the central store (v3-22, #32). Off unless
 * {@code igrp.audit.central.enabled} ({@code IGRP_AUDIT_CENTRAL_ENABLED}): AM
 * then reads the topic from {@code IGRP_AUDIT_CENTRAL_BOOTSTRAP_SERVERS}.
 *
 * <p>Each record's offset is committed after it is stored. When the store
 * fails (the database is down, say), the record is retried every 5 seconds
 * until it succeeds, so no event is lost and the order within a service holds.
 * A record that could never be stored (not an audit event, a field too long for
 * its column, an id already stored for another service) goes to the dead-letter
 * topic {@code igrp.audit.central.dead-letter-topic} ({@code igrp.audit.dlt}) and
 * the partition moves on. If even that publish fails, the record is retried.
 */
@Configuration
public class BusinessAuditListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessAuditListener.class);

    /** The listener's id, to start or stop it through the listener registry. */
    public static final String ID = "igrp-central-audit";

    private final BusinessAuditStore store;

    public BusinessAuditListener(BusinessAuditStore store) {
        this.store = store;
    }

    @KafkaListener(id = ID, topics = "${igrp.audit.central.topic:igrp.audit}",
            autoStartup = "${igrp.audit.central.enabled:false}")
    public void receive(ConsumerRecord<String, String> consumed) {
        if (!store.store(BusinessAuditStore.parse(consumed.key(), consumed.value()))) {
            LOGGER.debug("[AUDIT] Duplicate business audit event dropped");
        }
    }

    /** Retries a failed store until it succeeds; sends a record that can never be stored to the dead letters. */
    @Bean
    public CommonErrorHandler businessAuditErrorHandler(KafkaOperations<?, ?> kafka,
            @Value("${igrp.audit.central.dead-letter-topic:igrp.audit.dlt}") String deadLetterTopic) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafka, (consumed, failure) -> {
            LOGGER.error("[AUDIT] A record on {} cannot be stored; sent to {}: {}", consumed.topic(), deadLetterTopic,
                    failure.getMessage());
            return new TopicPartition(deadLetterTopic, -1);
        });
        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetters,
                new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(InvalidEventException.class, DataIntegrityViolationException.class);
        return handler;
    }
}
