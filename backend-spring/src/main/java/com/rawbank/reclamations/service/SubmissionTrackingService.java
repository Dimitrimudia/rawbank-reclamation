package com.rawbank.reclamations.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubmissionTrackingService {

    public record Status(String status, String complaintNumber, Instant updatedAt) {}

    private static class Entry {
        volatile String complaintNumber; // null while pending
        volatile Instant updatedAt = Instant.now();
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public void markPending(String trackingId) {
        store.computeIfAbsent(Objects.requireNonNull(trackingId), id -> new Entry()).updatedAt = Instant.now();
    }

    public void complete(String trackingId, String complaintNumber) {
        Entry e = store.computeIfAbsent(Objects.requireNonNull(trackingId), id -> new Entry());
        e.complaintNumber = complaintNumber;
        e.updatedAt = Instant.now();
    }

    public Mono<Status> get(String trackingId) {
        return Mono.justOrEmpty(Optional.ofNullable(store.get(trackingId))
                .map(e -> new Status(e.complaintNumber == null ? "pending" : "completed", e.complaintNumber, e.updatedAt)));
    }
}
