package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.central_audit.domain.service.BusinessAuditStore;
import cv.igrp.platform.access_management.central_audit.infrastructure.kafka.BusinessAuditListener;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v3-22 (#32): AM's central store of every service's business audit events,
 * fed from Kafka (igrp.audit), with a hash chain per source service.
 */
class BusinessAuditIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BusinessAuditStore store;
    @Autowired
    KafkaListenerEndpointRegistry listeners;

    private CompatSupport api;
    private String admin;
    private String service;

    @BeforeEach
    void setUp() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        service = "ba-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Events consumed from igrp.audit are stored in their service's chain; a duplicate is dropped, "
            + "and a record that cannot be stored goes to the dead letters without blocking the rest")
    void consumedFromKafka() throws Exception {
        String first = event(UUID.randomUUID(), service, "fatura.aprovada");
        String second = event(UUID.randomUUID(), service, "fatura.paga");
        String tooLong = event(UUID.randomUUID(), service, "e".repeat(129));
        String otherService = event(UUID.randomUUID(), service + "-forged", "fatura.anulada");
        MessageListenerContainer listener = Objects.requireNonNull(listeners.getListenerContainer(BusinessAuditListener.ID));
        listener.start();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CompatEnvironment.KAFKA.bootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            for (String message : List.of(first, "not an event", tooLong, otherService, second, first)) {
                producer.send(new ProducerRecord<>("igrp.audit", service, message)).get();
            }
        }

        // Every record handled (stored, dropped or dead-lettered) has its offset committed.
        awaitConsumed(listener.getGroupId());
        listener.stop();

        assertThat(jdbc.queryForList("""
                SELECT sequence_number || ' ' || event FROM t_business_audit_event WHERE service = ? ORDER BY 1""",
                String.class, service)).containsExactly("1 fatura.aprovada", "2 fatura.paga");
        assertThat(validate(service)).containsEntry("valid", true).containsEntry("checked", 2);
        assertThat(deadLetters(service)).hasSize(3).contains("not an event", tooLong, otherService);
    }

    @Test
    @DisplayName("Each service has its own chain; altering one row breaks that service's chain, not another's")
    void tamperingBreaksOnlyThatChain() {
        String other = service + "-other";
        for (int i = 0; i < 3; i++) {
            store.store(BusinessAuditStore.parse(event(UUID.randomUUID(), service, "evento." + i)));
            store.store(BusinessAuditStore.parse(event(UUID.randomUUID(), other, "evento." + i)));
        }
        assertThat(validate(service)).containsEntry("valid", true).containsEntry("checked", 3);

        // The table refuses changes; only its owner, disabling the trigger, could alter a row.
        assertThatThrownBy(() -> jdbc.update("UPDATE t_business_audit_event SET username = 'x' WHERE service = ?",
                service)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE t_business_audit_event")).hasMessageContaining("append-only");
        jdbc.execute("ALTER TABLE t_business_audit_event DISABLE TRIGGER no_update_delete_business_audit");
        try {
            jdbc.update("UPDATE t_business_audit_event SET resource = 'faturas/forged' "
                    + "WHERE service = ? AND sequence_number = 2", service);
        } finally {
            jdbc.execute("ALTER TABLE t_business_audit_event ENABLE TRIGGER no_update_delete_business_audit");
        }

        assertThat(validate(service)).containsEntry("valid", false).containsEntry("brokenSequence", 2)
                .containsEntry("reason", "hash does not match the row's fields");
        assertThat(validate(other)).containsEntry("valid", true).containsEntry("checked", 3);
    }

    @Test
    @DisplayName("Storing the same event twice keeps one; a message that is not an event is refused")
    void duplicatesAndInvalid() {
        UUID id = UUID.randomUUID();
        assertThat(store.store(BusinessAuditStore.parse(event(id, service, "evento")))).isTrue();
        assertThat(store.store(BusinessAuditStore.parse(event(id, service, "evento")))).isFalse();
        assertThat(count(service)).isOne();

        assertThatThrownBy(() -> BusinessAuditStore.parse("{\"id\":\"" + UUID.randomUUID() + "\"}"))
                .isInstanceOf(BusinessAuditStore.InvalidEventException.class);
        assertThatThrownBy(() -> BusinessAuditStore.parse("not json"))
                .isInstanceOf(BusinessAuditStore.InvalidEventException.class);
        assertThatThrownBy(() -> BusinessAuditStore.parse(event(UUID.randomUUID(), service, "e".repeat(129))))
                .isInstanceOf(BusinessAuditStore.InvalidEventException.class).hasMessageContaining("event");
        assertThatThrownBy(() -> BusinessAuditStore.parse(event(UUID.randomUUID(), service, "nul\\u0000")))
                .isInstanceOf(BusinessAuditStore.InvalidEventException.class).hasMessageContaining("NUL");
        assertThatThrownBy(() -> BusinessAuditStore.parse("another", event(UUID.randomUUID(), service, "evento")))
                .isInstanceOf(BusinessAuditStore.InvalidEventException.class).hasMessageContaining("key");
        // A payload's decimals are kept as sent, not rounded through a double.
        assertThat(BusinessAuditStore.parse(event(UUID.randomUUID(), service, "evento").replace("\"total\":100", "\"total\":0.10000000000000000001"))
                .payload()).contains("0.10000000000000000001");
    }

    @Test
    @DisplayName("The store is searchable by service, user and event; both routes are closed without their keys")
    void searchAndKeys() {
        store.store(BusinessAuditStore.parse(event(UUID.randomUUID(), service, "fatura.aprovada")));

        HttpResponse<String> found = api.get("/api/auth/audit/business?service=" + service + "&username=user-1", admin);
        assertThat(found.statusCode()).as(found.body()).isEqualTo(200);
        assertThat(CompatSupport.jsonArray(found.body())).hasSize(1);

        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Audit User', 'ACTIVE')""", userId, userId + "@example.cv",
                userId + "@example.cv");
        String user = api.userToken(userId, userId + "@example.cv");
        assertThat(api.get("/api/auth/audit/business?service=" + service, user).statusCode()).isEqualTo(403);
        assertThat(api.get("/api/auth/audit/business/validate?service=" + service, user).statusCode()).isEqualTo(403);
    }

    /** An event as the starter's relay publishes it (Audit.Event). */
    private static String event(UUID id, String service, String name) {
        return """
                {"id":"%s","event":"%s","resource":"faturas/42","payload":{"total":100,"moeda":"CVE"},
                 "username":"user-1","service":"%s","agent":null,"result":"SUCCESS","traceId":"trace-%s",
                 "occurredAt":"%s"}""".formatted(id, name, service, id, Instant.now());
    }

    /** Waits until the group has committed every offset of igrp.audit. */
    private static void awaitConsumed(String group) throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                CompatEnvironment.KAFKA.bootstrapServers()))) {
            long until = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (System.nanoTime() < until) {
                Map<TopicPartition, OffsetAndMetadata> committed =
                        admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
                Map<TopicPartition, OffsetSpec> latest = new java.util.HashMap<>();
                admin.describeTopics(List.of("igrp.audit")).allTopicNames().get().get("igrp.audit").partitions()
                        .forEach(p -> latest.put(new TopicPartition("igrp.audit", p.partition()), OffsetSpec.latest()));
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends = admin.listOffsets(latest).all().get();
                if (ends.entrySet().stream().allMatch(end -> committed.containsKey(end.getKey())
                        && committed.get(end.getKey()).offset() >= end.getValue().offset())) {
                    return;
                }
                LockSupport.parkNanos(Duration.ofMillis(250).toNanos());
            }
        }
        throw new AssertionError("igrp.audit was not consumed in time");
    }

    /** The dead letters keyed by {@code key}, read from the start of igrp.audit.dlt. */
    private static List<String> deadLetters(String key) {
        List<String> found = new java.util.ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CompatEnvironment.KAFKA.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("igrp.audit.dlt"));
            long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (found.size() < 3 && System.nanoTime() < until) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r.value());
                    }
                });
            }
        }
        return found;
    }

    private int count(String service) {
        return Objects.requireNonNullElse(jdbc.queryForObject(
                "SELECT count(*) FROM t_business_audit_event WHERE service = ?", Integer.class, service), 0);
    }

    private Map<String, Object> validate(String service) {
        HttpResponse<String> response = api.get("/api/auth/audit/business/validate?service=" + service, admin);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }
}
