package com.rawbank.reclamations.service;

import com.rawbank.reclamations.service.accounts.SharepointAuthService;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class SharepointListService {

    private static final Logger log = LoggerFactory.getLogger(SharepointListService.class);
    private final WebClient webClient;
    private final SharepointAuthService authService;
    private final String createItemUrl;
    private final String graphBaseUrl;
    private final String graphSiteId;
    private final String graphListId;
    private final boolean forceError;

    public SharepointListService(SharepointAuthService authService,
                                 @Value("${sharepoint.list.create.url:}") String createItemUrl,
                                 @Value("${graph.base.url:https://graph.microsoft.com/v1.0}") String graphBaseUrl,
                                 @Value("${graph.site.id:}") String graphSiteId,
                                 @Value("${graph.list.id:}") String graphListId,
                                 @Value("${app.test.forceError:false}") boolean forceError) {
        this.webClient = WebClient.builder().build();
        this.authService = authService;
        this.forceError = forceError;

        // Load optional .env from repo and module for fallbacks
        Dotenv dotenvRepo = null;
        Dotenv dotenvModule = null;
        try { dotenvRepo = Dotenv.configure().directory("../").ignoreIfMissing().load(); } catch (Exception ignore) {}
        try { dotenvModule = Dotenv.configure().directory(".").ignoreIfMissing().load(); } catch (Exception ignore) {}

        // Resolve create item URL from property or .env fallbacks
        String createItemUrlResolved = createItemUrl;
        if (!StringUtils.hasText(createItemUrlResolved)) {
            if (dotenvRepo != null) createItemUrlResolved = dotenvRepo.get("SHAREPOINT_LIST_CREATE_URL");
            if (!StringUtils.hasText(createItemUrlResolved) && dotenvModule != null) {
                createItemUrlResolved = dotenvModule.get("SHAREPOINT_LIST_CREATE_URL");
            }
        }

        // Resolve Graph configs from properties or .env fallbacks
        String graphBaseUrlResolved = StringUtils.hasText(graphBaseUrl) ? graphBaseUrl : (dotenvRepo != null ? dotenvRepo.get("GRAPH_BASE_URL") : null);
        String graphSiteIdResolved = StringUtils.hasText(graphSiteId) ? graphSiteId : (dotenvRepo != null ? dotenvRepo.get("GRAPH_SITE_ID") : null);
        String graphListIdResolved = StringUtils.hasText(graphListId) ? graphListId : (dotenvRepo != null ? dotenvRepo.get("GRAPH_LIST_ID") : null);

        if (!StringUtils.hasText(graphBaseUrlResolved) && dotenvModule != null) graphBaseUrlResolved = dotenvModule.get("GRAPH_BASE_URL");
        if (!StringUtils.hasText(graphSiteIdResolved) && dotenvModule != null) graphSiteIdResolved = dotenvModule.get("GRAPH_SITE_ID");
        if (!StringUtils.hasText(graphListIdResolved) && dotenvModule != null) graphListIdResolved = dotenvModule.get("GRAPH_LIST_ID");

        // Assign final fields once
        this.createItemUrl = createItemUrlResolved;
        this.graphBaseUrl = graphBaseUrlResolved;
        this.graphSiteId = graphSiteIdResolved;
        this.graphListId = graphListIdResolved;
    }

    public Mono<Map<String, Object>> createItem(Map<String, Object> payload) {
        if (!StringUtils.hasText(createItemUrl)) {
            return Mono.error(new IllegalStateException("URL création item SharePoint non configurée"));
        }
        // If Graph site/list ids are configured, use Graph API
        if (StringUtils.hasText(graphSiteId) && StringUtils.hasText(graphListId)) {
            String url = graphBaseUrl;
            if (!StringUtils.hasText(url)) url = "https://graph.microsoft.com/v1.0";
            String endpoint = url + "/sites/" + graphSiteId + "/lists/" + graphListId + "/items";
            // Graph expects { "fields": { ...mapped fields... } }
            Map<String, Object> graphBody = Map.of("fields", payload);
            return authService.getToken()
                .flatMap(token -> webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> { h.setBearerAuth(token); if (forceError) h.set("X-Force-Error", "true"); })
                    .bodyValue(graphBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new IllegalStateException("Erreur Graph create item (" + resp.statusCode().value() + "): " + body))))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}))
                .map(resp -> {
                // Normalize Graph response into { fields: { ... }, id: ... }
                Object fields = resp.get("fields");
                if (fields instanceof Map<?,?> f) {
                    @SuppressWarnings("unchecked") Map<String, Object> out = (Map<String, Object>) f;
                    Object id = resp.get("id");
                    if (id != null) out.put("id", id);
                    return out;
                }
                return resp;
                })
                .doOnSuccess(resp -> log.info("Item SharePoint créé via Graph"));
        }
        // Default: call direct SharePoint list create URL
        return authService.getToken()
            .flatMap(token -> webClient.post()
                .uri(createItemUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { h.setBearerAuth(token); if (forceError) h.set("X-Force-Error", "true"); })
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new IllegalStateException("Erreur SharePoint create item (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}))
            .doOnSuccess(resp -> log.info("Item SharePoint créé"));
    }
}
