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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class ElasticsearchService {

    private final WebClient webClient;
    private final String indexName;

    public ElasticsearchService(
            @Value("${elasticsearch.url:http://localhost:9200}") String esUrl,
            @Value("${elasticsearch.index:reclamations}") String indexName,
            @Value("${elasticsearch.username:}") String username,
            @Value("${elasticsearch.password:}") String password
    ) {
        this.indexName = indexName;
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(esUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(username)) {
            String token = Base64.getEncoder()
                    .encodeToString((username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
        }
        this.webClient = builder.build();
    }

    public Mono<Map<String, Object>> indexDocument(Map<String, Object> document) {
        String path = "/" + indexName + "/_doc";
        return webClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(document))
            .retrieve()
            .onStatus(HttpStatusCode::isError, resp ->
                resp.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new IllegalStateException(
                        "Erreur Elasticsearch (" + resp.statusCode().value() + "): " + body))))
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
