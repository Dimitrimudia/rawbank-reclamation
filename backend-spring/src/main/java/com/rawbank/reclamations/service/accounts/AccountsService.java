package com.rawbank.reclamations.service.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class AccountsService {

    private final WebClient webClient;
    private final AccountsAuthService authService;
    private final String accountsUrl;
    private final String httpMethod;

    public AccountsService(AccountsAuthService authService,
                           @Value("${accounts.api.url:}") String accountsUrl,
                           @Value("${accounts.api.method:POST}") String httpMethod) {
        this.webClient = WebClient.builder().build();
        this.authService = authService;
        this.accountsUrl = accountsUrl;
        this.httpMethod = (httpMethod == null ? "POST" : httpMethod).trim().toUpperCase();
    }

    public Mono<List<Map<String, Object>>> getAccounts(String clientId) {
        if (!StringUtils.hasText(accountsUrl)) {
            return Mono.error(new IllegalStateException("Accounts API non configurée"));
        }
        return authService.getAccessToken()
                .flatMap(token -> {
                    if ("POST".equals(httpMethod)) {
                        return webClient.post()
                                .uri(accountsUrl)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(Map.of("clientId", clientId)))
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, resp ->
                                resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new IllegalStateException(
                                        "Erreur Accounts API (" + resp.statusCode().value() + "): " + body))))
                            .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                                .collectList();
                    } else {
                        return webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path(accountsUrl)
                                        .queryParam("clientId", clientId)
                                        .build())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, resp ->
                                resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new IllegalStateException(
                                        "Erreur Accounts API (" + resp.statusCode().value() + "): " + body))))
                            .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                                .collectList();
                    }
                });
    }
}
