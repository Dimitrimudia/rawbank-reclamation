package com.rawbank.reclamations.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@SpringBootTest
@Testcontainers
class ComplaintsEventPublisherTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.security.protocol", () -> "PLAINTEXT");
    }

    @Autowired
    private ComplaintsEventPublisher publisher;

    @org.springframework.beans.factory.annotation.Value("${app.kafka.topics.complaints-raw:complaints_raw}")
    private String topic;

    @Test
    void shouldPublishComplaintPayloadToKafka() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("NUMEROCLIENT", "12345678");
        payload.put("EXTOURNE", true);
        payload.put("TYPERECLAMATION", "DELAI_TRAITEMENT");

        publisher.publishSubmitted(payload).block();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        try (Consumer<String, Object> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(java.util.List.of(topic));
            ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
            Assertions.assertFalse(records.isEmpty(), "Aucun message reçu sur le topic");
            Object value = records.iterator().next().value();
            Assertions.assertTrue(value instanceof Map, "Le message doit être une Map JSON");
            @SuppressWarnings("unchecked") Map<String, Object> received = (Map<String, Object>) value;
            Assertions.assertEquals("12345678", received.get("NUMEROCLIENT"));
            Assertions.assertEquals(true, received.get("EXTOURNE"));
            Assertions.assertEquals("DELAI_TRAITEMENT", received.get("TYPERECLAMATION"));
        }
    }
}
