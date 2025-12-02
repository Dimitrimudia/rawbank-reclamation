package com.rawbank.reclamations.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.complaints-raw:complaints_raw}")
    private String complaintsTopic;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public NewTopic complaintsTopic() {
        return TopicBuilder.name(complaintsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(Environment env) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // Injecter propriétés de sécurité si définies (SASL/TLS)
        copyIfPresent(env, configProps, "spring.kafka.properties.security.protocol", "security.protocol");
        copyIfPresent(env, configProps, "spring.kafka.properties.sasl.mechanism", "sasl.mechanism");
        copyIfPresent(env, configProps, "spring.kafka.properties.sasl.jaas.config", "sasl.jaas.config");
        copyIfPresent(env, configProps, "spring.kafka.properties.ssl.truststore.location", "ssl.truststore.location");
        copyIfPresent(env, configProps, "spring.kafka.properties.ssl.truststore.password", "ssl.truststore.password");
        copyIfPresent(env, configProps, "spring.kafka.properties.ssl.endpoint.identification.algorithm", "ssl.endpoint.identification.algorithm");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    private void copyIfPresent(Environment env, Map<String, Object> target, String springKey, String kafkaKey) {
        String value = env.getProperty(springKey);
        if (value != null && !value.isBlank()) {
            target.put(kafkaKey, value);
        }
    }
}
