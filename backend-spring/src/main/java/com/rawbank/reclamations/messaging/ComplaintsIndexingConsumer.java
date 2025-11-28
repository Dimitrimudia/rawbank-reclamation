package com.rawbank.reclamations.messaging;

import com.rawbank.reclamations.service.ElasticsearchService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ComplaintsIndexingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplaintsIndexingConsumer.class);

    private final ElasticsearchService elasticsearchService;

    public ComplaintsIndexingConsumer(ElasticsearchService elasticsearchService) {
        this.elasticsearchService = elasticsearchService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.complaints-raw:complaints_raw}",
            groupId = "${app.kafka.groups.indexer:reclamations-indexer}"
    )
    public void onComplaintSubmitted(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof Map)) {
            log.warn("[IDX] Event payload inattendu: {}", value == null ? "null" : value.getClass());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) value;
        log.info("[IDX] Reçu événement offset={} partition={}", record.offset(), record.partition());

        elasticsearchService.indexDocument(payload)
                .doOnSuccess(x -> log.info("[IDX] Indexation OK"))
                .doOnError(e -> log.error("[IDX] Erreur indexation: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .block();
    }
}
