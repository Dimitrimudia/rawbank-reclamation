package com.rawbank.reclamations.messaging;

import com.rawbank.reclamations.service.PowerAutomateService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ComplaintsPowerAutomateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplaintsPowerAutomateConsumer.class);

    private final PowerAutomateService powerAutomateService;

    public ComplaintsPowerAutomateConsumer(PowerAutomateService powerAutomateService) {
        this.powerAutomateService = powerAutomateService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.complaints-raw:complaints_raw}",
            groupId = "${app.kafka.groups.powerautomate:reclamations-pa-worker}"
    )
    public void onComplaintSubmitted(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof Map)) {
            log.warn("[PA] Event payload inattendu: {}", value == null ? "null" : value.getClass());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) value;
        log.info("[PA] Reçu événement offset={} partition={}", record.offset(), record.partition());

        powerAutomateService.submit(payload)
                .doOnSuccess(x -> log.info("[PA] PowerAutomate OK"))
                .doOnError(e -> log.error("[PA] Erreur PowerAutomate: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .block();
    }
}
