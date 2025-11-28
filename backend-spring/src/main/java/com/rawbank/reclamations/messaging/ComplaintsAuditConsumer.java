package com.rawbank.reclamations.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ComplaintsAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplaintsAuditConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.topics.complaints-raw:complaints_raw}",
            groupId = "${app.kafka.groups.audit:reclamations-audit}"
    )
    public void onComplaintSubmitted(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof Map)) {
            log.warn("[AUDIT] Event payload inattendu: {}", value == null ? "null" : value.getClass());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) value;
        log.info("[AUDIT] Réclamation immuable enregistrée key={} offset={} partition={} payloadKeys={}",
                record.key(), record.offset(), record.partition(), payload.keySet());
        // TODO: envoyer une notification (email/Slack/SMS) si nécessaire
    }
}
