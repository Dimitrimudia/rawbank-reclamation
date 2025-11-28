package com.rawbank.reclamations.service;

import com.rawbank.reclamations.messaging.ComplaintsEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class EventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherService.class);

    private final ComplaintsEventPublisher eventPublisher;

    public EventPublisherService(ComplaintsEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publication d'un payload final déjà enrichi/construit.
     * Ne fait aucun enrichissement: responsabilité du ComplaintsService.
     */
    public Mono<Void> publish(Map<String, Object> finalPayload) {
        log.info("[EVT] Publish complaint NUMEROCLIENT={} EXTOURNE={} TYPE={}", finalPayload.get("NUMEROCLIENT"), finalPayload.get("EXTOURNE"), finalPayload.get("TYPERECLAMATION"));
        log.debug("[EVT] Final payload: {}", finalPayload);
        return eventPublisher.publishSubmitted(finalPayload);
    }
}
