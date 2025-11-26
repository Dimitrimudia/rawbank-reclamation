package com.rawbank.reclamations.service.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

@Service
public class AccountsAuthService {

    private final WebClient webClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    public AccountsAuthService(
            @Value("${accounts.auth.tokenUrl:}") String tokenUrl,
            @Value("${accounts.auth.clientId:}") String clientId,
            @Value("${accounts.auth.clientSecret:}") String clientSecret,
            @Value("${accounts.auth.scope:}") String scope
    ) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.webClient = WebClient.builder().build();
    }

    public Mono<String> getAccessToken() {
        if (!StringUtils.hasText(tokenUrl)) {
            return Mono.error(new IllegalStateException("Token URL non configurée"));
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        if (StringUtils.hasText(clientId)) form.add("client_id", clientId);
        if (StringUtils.hasText(clientSecret)) form.add("client_secret", clientSecret);
        if (StringUtils.hasText(scope)) form.add("scope", scope);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp ->
                resp.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new IllegalStateException(
                        "Erreur token (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .switchIfEmpty(Mono.error(new IllegalStateException("Réponse token vide")))
            .flatMap(map -> {
                String token = map == null ? null : (String) map.get("access_token");
                if (!StringUtils.hasText(token)) {
                return Mono.error(new IllegalStateException("Token manquant dans la réponse"));
                }
                return Mono.just(token);
            });
    }
}
