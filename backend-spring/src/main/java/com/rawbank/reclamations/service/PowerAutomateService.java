package com.rawbank.reclamations.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;
import java.util.Objects;

@Service
public class PowerAutomateService {

    private final WebClient webClient;
    private final WebClient tokenClient;
    private final String endpoint;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final boolean forceError;

    public PowerAutomateService(
            @Value("${powerautomate.url:}") String endpoint,
            @Value("${powerautomate.apiKeyHeaderName:}") String apiKeyHeaderName,
                @Value("${powerautomate.apiKey:}") String apiKey,
                @Value("${powerautomate.tokenUrl:}") String tokenUrl,
                @Value("${powerautomate.clientId:}") String clientId,
                @Value("${powerautomate.clientSecret:}") String clientSecret,
                @Value("${powerautomate.scope:}") String scope
                    , @Value("${app.test.forceError:false}") boolean forceError
    ) {
            this.endpoint = endpoint;
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scope = scope;
            this.forceError = forceError;

            WebClient.Builder builder = WebClient.builder();
        if (StringUtils.hasText(apiKeyHeaderName) && StringUtils.hasText(apiKey)) {
            builder.defaultHeader(apiKeyHeaderName, apiKey);
        }
        builder.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        this.webClient = builder.build();

            // Token client sans Content-Type par défaut (sera défini par requête)
            this.tokenClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<Map<String, Object>> submit(Map<String, Object> payload) {
        if (!StringUtils.hasText(endpoint)) {
            return Mono.error(new IllegalStateException("Power Automate endpoint non configuré"));
        }
            // Si tokenUrl et credentials fournis, obtenir un token et appeler avec Authorization
            if (StringUtils.hasText(tokenUrl) && StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)) {
                    return getAccessToken()
                        .flatMap(token -> webClient.post()
                            .uri(endpoint)
                            .headers(h -> {
                            h.setBearerAuth(token);
                            if (forceError) h.set("X-Force-Error", "true");
                            })
                        .body(BodyInserters.fromValue(payload))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IllegalStateException(
                                    "Erreur Power Automate (" + resp.statusCode().value() + "): " + body))))
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}));
            }

            // Sinon, appel direct (clé API éventuelle déjà injectée en header par défaut)
            return webClient.post()
                .uri(endpoint)
                .headers(h -> { if (forceError) h.set("X-Force-Error", "true"); })
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new IllegalStateException(
                            "Erreur Power Automate (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

            private Mono<String> getAccessToken() {
            if (!StringUtils.hasText(tokenUrl)) {
                return Mono.error(new IllegalStateException("powerautomate.tokenUrl non configuré"));
            }
            org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", Objects.toString(clientId, ""));
            form.add("client_secret", Objects.toString(clientSecret, ""));
            if (StringUtils.hasText(scope)) form.add("scope", scope);

            return tokenClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new IllegalStateException(
                            "Erreur token Power Automate (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(map -> {
                    Object at = map.get("access_token");
                    return at == null ? null : at.toString();
                })
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(new IllegalStateException("Réponse token invalide (access_token manquant)")));
            }
}
