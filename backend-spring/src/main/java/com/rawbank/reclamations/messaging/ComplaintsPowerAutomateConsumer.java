package com.rawbank.reclamations.messaging;

import com.rawbank.reclamations.service.PowerAutomateService;
import com.rawbank.reclamations.service.SubmissionTrackingService;
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
    private final SubmissionTrackingService trackingService;

    public ComplaintsPowerAutomateConsumer(PowerAutomateService powerAutomateService,
                                           SubmissionTrackingService trackingService) {
        this.powerAutomateService = powerAutomateService;
        this.trackingService = trackingService;
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

        String trackingId = String.valueOf(payload.getOrDefault("TRACKINGID", ""));
        String already = extractComplaintNumber(payload);
        if (already != null && !already.isBlank()) {
            if (!trackingId.isBlank()) trackingService.complete(trackingId, already);
            log.info("[PA] Numéro déjà présent dans l'événement, aucun appel PA. trackingId={} numéro={}", trackingId, already);
            return;
        }

        powerAutomateService.submit(payload)
            .doOnSuccess(resp -> {
                String complaintNumber = extractComplaintNumber(resp);
                if (!trackingId.isBlank() && complaintNumber != null && !complaintNumber.isBlank()) {
                    trackingService.complete(trackingId, complaintNumber);
                    log.info("[PA] Complète trackingId={} numéro={}", trackingId, complaintNumber);
                } else {
                    log.info("[PA] PowerAutomate OK (trackingId='{}', numero introuvable)", trackingId);
                }
            })
            .doOnError(e -> log.error("[PA] Erreur PowerAutomate: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())
            .block();
    }

    private String extractComplaintNumber(Map<String, Object> response) {
        if (response == null) return null;
        String[] keys = new String[] {"numero", "NUMERO", "complaintNumber", "reference", "ticket", "id"};
        for (String k : keys) {
            Object v = response.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
            if (v instanceof Number n) return String.valueOf(n);
        }
        return null;
    }
}
