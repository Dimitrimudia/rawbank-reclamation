package com.rawbank.reclamations.web;

import com.rawbank.reclamations.service.accounts.AccountsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final AccountsService accountsService;

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getAccounts(@RequestParam("clientId") String clientId) {
        return accountsService.getAccounts(clientId)
                .map(list -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("accounts", list);
                    return ResponseEntity.ok(body);
                })
                .onErrorResume(ex -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("ok", false);
                    err.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(err));
                });
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> postAccounts(@RequestBody Map<String, Object> bodyIn) {
        Object raw = bodyIn == null ? null : bodyIn.get("clientId");
        String clientId = raw == null ? null : String.valueOf(raw);
        return accountsService.getAccounts(clientId)
                .map(list -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("accounts", list);
                    return ResponseEntity.ok(body);
                })
                .onErrorResume(ex -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("ok", false);
                    err.put("error", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(err));
                });
    }
}
