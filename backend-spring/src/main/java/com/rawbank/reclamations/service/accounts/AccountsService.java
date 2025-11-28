package com.rawbank.reclamations.service.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AccountsService {

    private final WebClient webClient;
    private final String accountDetailsUrl;
    private final SharepointAuthService sharepointAuthService;
    private static final Logger log = LoggerFactory.getLogger(AccountsService.class);

    public AccountsService(SharepointAuthService sharepointAuthService,
                           @Value("${accounts.details.url:}") String accountDetailsUrl) {
        this.webClient = WebClient.builder().build();
        this.sharepointAuthService = sharepointAuthService;
        // Charger depuis .env si non fourni par properties/env
        Dotenv dotenv = Dotenv.configure()
                .directory("../../")
                .ignoreIfMissing()
                .load();
        String envUrl = dotenv.get("SHAREPOINT_ACCOUNT_DETAILS_URL");
        this.accountDetailsUrl = StringUtils.hasText(accountDetailsUrl) ? accountDetailsUrl : envUrl;
    }

    /**
     * Appelle l'API externe getAccountDetail et retourne une liste formatée
     * agencyCode-accountNumber-suffix pour chaque compte.
     */
    public Mono<List<String>> getAccounts(String clientId) {
        if (!StringUtils.hasText(accountDetailsUrl)) {
            return Mono.error(new IllegalStateException("URL détails comptes non configurée"));
        }
        if (!StringUtils.hasText(clientId) || !clientId.matches("^\\d{8}$")) {
            return Mono.error(new IllegalArgumentException("clientId invalide (8 chiffres requis)"));
        }
        log.debug("Appel getAccountDetail pour clientId={}", clientId);
        return sharepointAuthService.getToken()
            .flatMap(token -> webClient.post()
                .uri(accountDetailsUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token))
                .bodyValue(Map.of("customerCode", clientId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new IllegalStateException("Erreur API détails (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}))
                .map(root -> {
                    log.debug("Réponse details reçue: {}", root != null);
                    if (root == null) return Collections.emptyList();
                    Object detailObj = root.get("cutomerDetail"); // orthographe fournie dans le payload
                    if (!(detailObj instanceof Map)) return Collections.emptyList();
                    Map<?, ?> detail = (Map<?, ?>) detailObj;
                    Object accountListObj = detail.get("accountList");
                    if (!(accountListObj instanceof List)) return Collections.emptyList();
                    List<?> rawList = (List<?>) accountListObj;
                    List<String> formatted = new ArrayList<>();
                    for (Object o : rawList) {
                        if (o instanceof Map<?, ?> acc) {
                            Object agencyCode = acc.get("agencyCode");
                            Object accountNumber = acc.get("accountNumber");
                            Object suffix = acc.get("suffix");
                            if (agencyCode != null && accountNumber != null && suffix != null) {
                                formatted.add(agencyCode + "-" + accountNumber + "-" + suffix);
                            }
                        }
                    }
                    log.info("Comptes formatés: {} items", formatted.size());
                    return formatted;
                });
    }

    /**
     * Retourne la section de détail client telle que renvoyée par l'API externe.
     * Permet d'enrichir le payload de réclamation côté backend (nom client, agence, etc.).
     */
    public Mono<Map<String, Object>> getCustomerDetail(String clientId) {
        if (!StringUtils.hasText(accountDetailsUrl)) {
            return Mono.error(new IllegalStateException("URL détails comptes non configurée"));
        }
        if (!StringUtils.hasText(clientId) || !clientId.matches("^\\d{8}$")) {
            return Mono.error(new IllegalArgumentException("clientId invalide (8 chiffres requis)"));
        }
        log.debug("Appel getAccountDetail (detail) pour clientId={}", clientId);
        return sharepointAuthService.getToken()
            .flatMap(token -> webClient.post()
                .uri(accountDetailsUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token))
                .bodyValue(Map.of("customerCode", clientId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new IllegalStateException("Erreur API détails (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}))
            .map(root -> {
                Object detailObj = root == null ? null : root.get("cutomerDetail");
                if (!(detailObj instanceof Map)) {
                    log.warn("Détail client absent ou invalide dans la réponse");
                    return Collections.<String, Object>emptyMap();
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> detail = (Map<String, Object>) detailObj;
                log.info("Détail client récupéré (clés={})", detail.keySet());
                return detail;
            });
    }
}
