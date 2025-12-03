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
    // Graph client credentials
    private final String graphTenantId;
    private final String graphClientId;
    private final String graphClientSecret;
    private final String graphScope;
    private static final Logger log = LoggerFactory.getLogger(SharepointAuthService.class);

    public SharepointAuthService(
            @Value("${sharepoint.auth.url:}") String authUrl,
            @Value("${sharepoint.auth.username:}") String username,
            @Value("${sharepoint.auth.password:}") String password,
            @Value("${graph.tenant.id:}") String graphTenantId,
            @Value("${graph.client.id:}") String graphClientId,
            @Value("${graph.client.secret:}") String graphClientSecret,
            @Value("${graph.scope:https://graph.microsoft.com/.default}") String graphScope
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
        // Graph credentials from properties or .env
        this.graphTenantId = StringUtils.hasText(graphTenantId) ? graphTenantId : dotenv.get("GRAPH_TENANT_ID");
        this.graphClientId = StringUtils.hasText(graphClientId) ? graphClientId : dotenv.get("GRAPH_CLIENT_ID");
        this.graphClientSecret = StringUtils.hasText(graphClientSecret) ? graphClientSecret : dotenv.get("GRAPH_CLIENT_SECRET");
        this.graphScope = StringUtils.hasText(graphScope) ? graphScope : (dotenv.get("GRAPH_SCOPE") != null ? dotenv.get("GRAPH_SCOPE") : "https://graph.microsoft.com/.default");
    }

    public Mono<String> getToken() {
        // Prefer Graph client credentials if configured
        if (StringUtils.hasText(graphTenantId) && StringUtils.hasText(graphClientId) && StringUtils.hasText(graphClientSecret)) {
            String tokenUrl = "https://login.microsoftonline.com/" + graphTenantId + "/oauth2/v2.0/token";
            log.debug("Authentification Graph via client credentials {}");
            return webClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("client_id=" + encode(graphClientId)
                            + "&client_secret=" + encode(graphClientSecret)
                            + "&grant_type=client_credentials"
                            + "&scope=" + encode(graphScope))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(b -> Mono.error(new IllegalStateException("Erreur auth Graph (" + resp.statusCode().value() + ")"))))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .flatMap(map -> {
                        Object tok = map == null ? null : map.get("access_token");
                        if (tok == null || !StringUtils.hasText(String.valueOf(tok))) {
                            return Mono.error(new IllegalStateException("access_token absent dans la réponse Graph"));
                        }
                        log.info("Token Graph obtenu");
                        return Mono.just(String.valueOf(tok));
                    });
        }
        // Fallback to legacy SharePoint auth endpoint
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

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
