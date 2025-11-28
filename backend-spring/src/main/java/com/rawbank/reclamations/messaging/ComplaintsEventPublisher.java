package com.rawbank.reclamations.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ComplaintsEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public ComplaintsEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                    @Value("${app.kafka.topics.complaints-raw:complaints_raw}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public Mono<Void> publishSubmitted(Map<String, Object> finalPayload) {
        return Mono.create(sink ->
                kafkaTemplate.send(topic, finalPayload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                sink.error(ex);
                            } else {
                                sink.success();
                            }
                        })
        );
    }
}
