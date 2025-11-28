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
        // Publication asynchrone non bloquante
        return complaintsService.submit(payload)
                .then(Mono.fromSupplier(() -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("queued", true);
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
                }))
                .onErrorResume(ex -> Mono.fromSupplier(() -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("ok", false);
                    err.put("error", "Payload invalide ou erreur serveur");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
                }));
    }
}
