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
    private final String accountDetailsByPhoneUrl;
    private final SharepointAuthService sharepointAuthService;
    private final boolean forceError;
    private static final Logger log = LoggerFactory.getLogger(AccountsService.class);

    public AccountsService(SharepointAuthService sharepointAuthService,
                           @Value("${accounts.details.url:}") String accountDetailsUrl,
                           @Value("${accounts.details.byphone.url:}") String accountDetailsByPhoneUrl,
                           @Value("${app.test.forceError:false}") boolean forceError) {
        this.webClient = WebClient.builder().build();
        this.sharepointAuthService = sharepointAuthService;
        this.forceError = forceError;
        // Charger depuis .env si non fourni par properties/env
        // Priorité: @Value > .env racine du repo > .env du module
        String tmpAccountUrl = null;
        String tmpPhoneUrl = null;
        try {
            Dotenv dotenvRepo = Dotenv.configure().directory("../").ignoreIfMissing().load();
            tmpAccountUrl = dotenvRepo.get("SHAREPOINT_ACCOUNT_DETAILS_URL");
            tmpPhoneUrl = dotenvRepo.get("SHAREPOINT_ACCOUNT_DETAILS_BY_PHONE_URL");
        } catch (Exception ignore) {}
        if (!StringUtils.hasText(tmpAccountUrl) || !StringUtils.hasText(tmpPhoneUrl)) {
            try {
                Dotenv dotenvModule = Dotenv.configure().directory(".").ignoreIfMissing().load();
                if (!StringUtils.hasText(tmpAccountUrl)) tmpAccountUrl = dotenvModule.get("SHAREPOINT_ACCOUNT_DETAILS_URL");
                if (!StringUtils.hasText(tmpPhoneUrl)) tmpPhoneUrl = dotenvModule.get("SHAREPOINT_ACCOUNT_DETAILS_BY_PHONE_URL");
            } catch (Exception ignore) {}
        }
        this.accountDetailsUrl = StringUtils.hasText(accountDetailsUrl) ? accountDetailsUrl : tmpAccountUrl;
        this.accountDetailsByPhoneUrl = StringUtils.hasText(accountDetailsByPhoneUrl) ? accountDetailsByPhoneUrl : tmpPhoneUrl;
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
                .headers(h -> {
                    h.setBearerAuth(token);
                    if (forceError) h.set("X-Force-Error", "true");
                })
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
                .headers(h -> {
                    h.setBearerAuth(token);
                    if (forceError) h.set("X-Force-Error", "true");
                })
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

    /**
     * Liste des comptes formatés agencyCode-accountNumber-suffix à partir d'un numéro de téléphone.
     */
    public Mono<List<String>> getAccountsByPhone(String phone) {
        return getCustomerDetailByPhone(phone)
                .map(detail -> {
                    if (detail == null) return Collections.emptyList();
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
                    log.info("Comptes formatés (téléphone): {} items", formatted.size());
                    return formatted;
                });
    }

    /**
     * Retourne le détail client à partir d'un numéro de téléphone (10 chiffres).
     * Normalise la réponse pour exposer la même structure que getCustomerDetail (customerName, accountList, ...).
     */
    public Mono<Map<String, Object>> getCustomerDetailByPhone(String phone) {
        if (!StringUtils.hasText(accountDetailsByPhoneUrl)) {
            return Mono.error(new IllegalStateException("URL détails comptes (par téléphone) non configurée"));
        }
        if (!StringUtils.hasText(phone)) {
            return Mono.error(new IllegalArgumentException("Numéro de téléphone requis"));
        }
        String digits = phone.replaceAll("\\D", "");
        if (!digits.matches("^\\d{10}$")) {
            return Mono.error(new IllegalArgumentException("Numéro de téléphone invalide (10 chiffres requis)"));
        }
        log.debug("Appel getAccountDetailByPhone pour phone={}", digits);
        return sharepointAuthService.getToken()
            .flatMap(token -> webClient.post()
                .uri(accountDetailsByPhoneUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(token);
                    if (forceError) h.set("X-Force-Error", "true");
                })
                .bodyValue(Map.of("phoneNumber", digits))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(new IllegalStateException("Erreur API détails par téléphone (" + resp.statusCode().value() + "): " + body))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}))
            .map(root -> normalizeDetailResponse(root));
    }

    /**
     * Certaines APIs peuvent renvoyer "cutomerDetail" (sic) ou "customerDetail" ou tout à la racine.
     * On tente de retourner un Map cohérent.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeDetailResponse(Map<String, Object> root) {
        if (root == null) return Collections.emptyMap();
        Object detailObj = root.get("cutomerDetail");
        if (!(detailObj instanceof Map)) {
            detailObj = root.get("customerDetail");
        }
        if (detailObj instanceof Map) {
            return (Map<String, Object>) detailObj;
        }

        // Nouvelle forme (par téléphone): { status, message, rawComptes: [ { numero, codeDev, ... } ] }
        Object rawComptesObj = root.get("rawComptes");
        if (rawComptesObj instanceof List<?> rawComptes) {
            List<Map<String, Object>> accountList = new ArrayList<>();
            List<String> candidateNames = new ArrayList<>();
            for (Object o : rawComptes) {
                if (o instanceof Map<?, ?> acc) {
                    Object numero = acc.get("numero");
                    if (numero instanceof String s && s.contains("-")) {
                        String[] parts = s.trim().split("-");
                        if (parts.length >= 3) {
                            String agencyCode = parts[0].trim();
                            String accountNumber = parts[1].trim();
                            String suffix = parts[2].trim();
                            Map<String, Object> m = new java.util.HashMap<>();
                            m.put("agencyCode", agencyCode);
                            m.put("accountNumber", accountNumber);
                            m.put("suffix", suffix);
                            Object codeDev = acc.get("codeDev");
                            if (codeDev instanceof String cs && cs.matches("^\\d+$")) {
                                try {
                                    m.put("currencyCode", Integer.parseInt(cs));
                                } catch (NumberFormatException ignore) {
                                    m.put("currencyCode", cs);
                                }
                            } else if (codeDev instanceof Number n) {
                                m.put("currencyCode", n.intValue());
                            }
                            accountList.add(m);

                            Object intitule = acc.get("intitule");
                            if (intitule instanceof String it && !it.isBlank()) {
                                String name = it;
                                // Heuristique: prendre la partie avant "V/C"
                                int idx = it.toUpperCase().indexOf("V/C");
                                if (idx > 0) name = it.substring(0, idx).trim();
                                candidateNames.add(name);
                            }
                        }
                    }
                }
            }
            Map<String, Object> normalized = new java.util.HashMap<>();
            normalized.put("accountList", accountList);
            // Déduire customerName si tous identiques
            if (!candidateNames.isEmpty()) {
                String first = candidateNames.get(0);
                boolean allSame = candidateNames.stream().allMatch(first::equals);
                if (allSame && !first.isBlank()) {
                    normalized.put("customerName", first);
                }
            }
            log.debug("Normalisation rawComptes -> accountList: {} comptes", accountList.size());
            return normalized;
        }

        // En dernier recours, retourner la racine telle quelle
        log.warn("Structure inattendue pour détail client par téléphone; retour racine");
        return root;
    }
}
