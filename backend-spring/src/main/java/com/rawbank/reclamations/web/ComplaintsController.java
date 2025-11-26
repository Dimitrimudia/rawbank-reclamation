package com.rawbank.reclamations.web;

import com.rawbank.reclamations.service.ElasticsearchService;
import com.rawbank.reclamations.service.PowerAutomateService;
import com.rawbank.reclamations.service.PayloadBuilderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintsController {

    private final ElasticsearchService elasticsearchService;
    private final PowerAutomateService powerAutomateService;
    private final PayloadBuilderService payloadBuilderService;

    public ComplaintsController(ElasticsearchService elasticsearchService, PowerAutomateService powerAutomateService, PayloadBuilderService payloadBuilderService) {
        this.elasticsearchService = elasticsearchService;
        this.powerAutomateService = powerAutomateService;
        this.payloadBuilderService = payloadBuilderService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> submit(@RequestBody @Validated Map<String, Object> payload) {
        // Construire le payload final (incluant tous les champs par défaut JSON)
        Map<String, Object> finalPayload = payloadBuilderService.build(payload);
        // 1) Enregistrer via Power Automate
        return powerAutomateService.submit(finalPayload)
                .flatMap(paRes -> {
                    // 2) Optionnel: indexer dans Elasticsearch (si configuré)
                    return elasticsearchService.indexDocument(finalPayload)
                            .defaultIfEmpty(Map.<String, Object>of())
                            .map(esRes -> {
                                Map<String, Object> body = new HashMap<>();
                                body.put("ok", true);
                                body.put("powerAutomate", paRes);
                                if (esRes != null) body.put("elasticsearch", esRes);
                                return ResponseEntity.status(HttpStatus.CREATED).body(body);
                            });
                })
                .onErrorResume(ex -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("ok", false);
                    err.put("error", "Payload invalide ou erreur serveur");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err));
                });
    }
}
