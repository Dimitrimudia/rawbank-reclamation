package com.rawbank.reclamations.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerErrorHandlerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.complaints-raw:complaints_raw}")
    private String complaintsTopic;

    @Bean
    public NewTopic complaintsDlqTopic() {
        return new NewTopic(complaintsTopic + ".DLQ", 3, (short) 1);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(Environment env) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonDeserializer.class);
        props.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "*");
        // Désactivation des en-têtes de type (le producteur les supprime) et définition d'un type par défaut Map
        props.put(org.springframework.kafka.support.serializer.JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        copyIfPresent(env, props, "spring.kafka.properties.security.protocol", "security.protocol");
        copyIfPresent(env, props, "spring.kafka.properties.sasl.mechanism", "sasl.mechanism");
        copyIfPresent(env, props, "spring.kafka.properties.sasl.jaas.config", "sasl.jaas.config");
        copyIfPresent(env, props, "spring.kafka.properties.ssl.truststore.location", "ssl.truststore.location");
        copyIfPresent(env, props, "spring.kafka.properties.ssl.truststore.password", "ssl.truststore.password");
        copyIfPresent(env, props, "spring.kafka.properties.ssl.endpoint.identification.algorithm", "ssl.endpoint.identification.algorithm");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLQ", record.partition()));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private void copyIfPresent(Environment env, Map<String, Object> target, String springKey, String kafkaKey) {
        String value = env.getProperty(springKey);
        if (value != null && !value.isBlank()) {
            target.put(kafkaKey, value);
        }
    }
}
