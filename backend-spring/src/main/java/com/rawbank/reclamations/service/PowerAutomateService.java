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

@Service
public class PowerAutomateService {

    private final WebClient webClient;
    private final String endpoint;

    public PowerAutomateService(
            @Value("${powerautomate.url:}") String endpoint,
            @Value("${powerautomate.apiKeyHeaderName:}") String apiKeyHeaderName,
            @Value("${powerautomate.apiKey:}") String apiKey
    ) {
        this.endpoint = endpoint;
        WebClient.Builder builder = WebClient.builder();
        if (StringUtils.hasText(apiKeyHeaderName) && StringUtils.hasText(apiKey)) {
            builder.defaultHeader(apiKeyHeaderName, apiKey);
        }
        builder.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        this.webClient = builder.build();
    }

    public Mono<Map<String, Object>> submit(Map<String, Object> payload) {
        if (!StringUtils.hasText(endpoint)) {
            return Mono.error(new IllegalStateException("Power Automate endpoint non configuré"));
        }
        return webClient.post()
                .uri(endpoint)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IllegalStateException(
                                        "Erreur Power Automate (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
