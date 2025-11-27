package com.rawbank.reclamations.service.accounts;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SharepointAuthService {

    private final WebClient webClient;
    private final String authUrl;
    private final String username;
    private final String password;
    private static final Logger log = LoggerFactory.getLogger(SharepointAuthService.class);

    public SharepointAuthService(
            @Value("${sharepoint.auth.url:}") String authUrl,
            @Value("${sharepoint.auth.username:}") String username,
            @Value("${sharepoint.auth.password:}") String password
    ) {
        this.webClient = WebClient.builder().build();
        // Permet de charger depuis .env si non fourni par properties/env
        Dotenv dotenv = Dotenv.configure()
            .directory("../../") // racine du workspace
                .ignoreIfMissing()
                .load();
        this.authUrl = StringUtils.hasText(authUrl) ? authUrl : dotenv.get("SHAREPOINT_AUTH_URL");
        this.username = StringUtils.hasText(username) ? username : dotenv.get("SHAREPOINT_USERNAME");
        this.password = StringUtils.hasText(password) ? password : dotenv.get("SHAREPOINT_PASSWORD");
    }

    public Mono<String> getToken() {
        if (!StringUtils.hasText(authUrl)) {
            return Mono.error(new IllegalStateException("URL d'authentification SharePoint non configurée"));
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Mono.error(new IllegalStateException("Identifiants SharePoint manquants (username/password)"));
        }
        log.debug("Authentification SharePoint via {}", authUrl);
        Map<String, String> body = Map.of(
                "username", username,
                "password", password
        );
        return webClient.post()
                .uri(authUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(b -> Mono.error(new IllegalStateException("Erreur auth SharePoint (" + resp.statusCode().value() + ")"))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(map -> {
                    Object tok = map == null ? null : map.get("token");
                    if (tok == null || !StringUtils.hasText(String.valueOf(tok))) {
                        return Mono.error(new IllegalStateException("Token absent dans la réponse d'authentification"));
                    }
                    log.info("Token SharePoint obtenu");
                    return Mono.just(String.valueOf(tok));
                });
    }
}
