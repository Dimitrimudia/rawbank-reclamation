package com.rawbank.reclamations.web;

import com.rawbank.reclamations.service.accounts.AccountsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final AccountsService accountsService;
    private static final Logger log = LoggerFactory.getLogger(AccountsController.class);

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> getAccounts(@RequestBody Map<String, Object> bodyIn) {
        Object raw = bodyIn == null ? null : bodyIn.get("clientId");
        String clientId = raw == null ? null : String.valueOf(raw);
        log.debug("POST /api/accounts clientId={}", clientId);
        if (clientId == null || !clientId.matches("^\\d{8}$")) {
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", "clientId invalide: doit contenir exactement 8 chiffres");
            return Mono.just(ResponseEntity.badRequest().body(err));
        }
        return accountsService.getAccounts(clientId)
                .map(list -> {
                    log.info("Comptes récupérés: {} éléments pour client {}", list.size(), clientId);
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("accounts", list); // liste formatée agencyCode-accountNumber-suffix
                    return ResponseEntity.ok(body);
                })
                .onErrorResume(ex -> {
                    log.error("Erreur lors de la récupération des comptes pour {}: {}", clientId, ex.getMessage());
                    Map<String, Object> err = new HashMap<>();
                    err.put("ok", false);
                    err.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(err));
                });
    }

    // Limité à un seul endpoint pour récupérer la liste des comptes
}
