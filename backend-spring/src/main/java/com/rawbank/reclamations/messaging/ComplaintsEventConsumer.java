package com.rawbank.reclamations.messaging;

import com.rawbank.reclamations.service.ElasticsearchService;
import com.rawbank.reclamations.service.PowerAutomateService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.kafka.enable-legacy-consumer", havingValue = "true")
public class ComplaintsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplaintsEventConsumer.class);

    private final PowerAutomateService powerAutomateService;
    private final ElasticsearchService elasticsearchService;

    public ComplaintsEventConsumer(PowerAutomateService powerAutomateService,
                                   ElasticsearchService elasticsearchService) {
        this.powerAutomateService = powerAutomateService;
        this.elasticsearchService = elasticsearchService;
    }

    @KafkaListener(topics = "#{'${app.kafka.topics.complaints-submitted:complaints.submitted}'}",
                   groupId = "${spring.kafka.consumer.group-id:reclamations-consumer}")
    public void onComplaintSubmitted(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof Map)) {
            log.warn("Event payload inattendu: {}", value == null ? "null" : value.getClass());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) value;
        log.info("Reçu événement complaints.submitted offset={} partition={}", record.offset(), record.partition());

        powerAutomateService.submit(payload)
                .flatMap(pa -> {
                    log.info("PowerAutomate OK");
                    return elasticsearchService.indexDocument(payload).defaultIfEmpty(Map.of());
                })
                .doOnError(e -> log.error("Erreur traitement asynchrone de la réclamation: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .block(); // worker: acceptable de bloquer pour garantir le traitement séquentiel ici
    }
}
