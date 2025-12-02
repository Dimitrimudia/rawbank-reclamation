package com.rawbank.reclamations.web;

import com.rawbank.reclamations.service.ComplaintsService;
import com.rawbank.reclamations.model.ComplaintDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintsController {

    private final ComplaintsService complaintsService;

    private static final Logger log = LoggerFactory.getLogger(ComplaintsController.class);

    public ComplaintsController(ComplaintsService complaintsService) {
        this.complaintsService = complaintsService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> submit(@RequestBody @Validated ComplaintDto payload) {
        log.debug("POST /api/complaints payload DTO in");
        // Retourner directement le numéro de réclamation officiel fourni par Power Automate
        return complaintsService.submit(payload)
                .map(complaintNumber -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("complaintNumber", complaintNumber);
                    return ResponseEntity.status(HttpStatus.CREATED).body(body);
                })
                .onErrorResume(ex -> Mono.fromSupplier(() -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("ok", false);
                    err.put("error", "Payload invalide ou erreur serveur");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
                }));
    }

    // Endpoint de statut conservé pour compat, mais non requis si on renvoie le numéro immédiatement.
    @GetMapping("/{trackingId}")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus(@PathVariable String trackingId) {
        return complaintsService.status(trackingId)
                .map(status -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("status", status.status());
                    if (status.complaintNumber() != null) body.put("complaintNumber", status.complaintNumber());
                    return ResponseEntity.status("completed".equals(status.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "unknown")));
    }
}
