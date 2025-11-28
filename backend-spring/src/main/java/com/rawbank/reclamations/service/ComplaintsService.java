package com.rawbank.reclamations.service;

import com.rawbank.reclamations.model.ComplaintDto;
import com.rawbank.reclamations.service.accounts.AccountsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ComplaintsService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintsService.class);

    private final EventPublisherService eventPublisherService;
    private final PayloadBuilderService payloadBuilderService;
    private final AccountsService accountsService;

    public ComplaintsService(EventPublisherService eventPublisherService,
                             PayloadBuilderService payloadBuilderService,
                             AccountsService accountsService) {
        this.eventPublisherService = eventPublisherService;
        this.payloadBuilderService = payloadBuilderService;
        this.accountsService = accountsService;
    }

    /**
     * Orchestration métier: validation déjà assurée par le contrôleur.
     * Publie l'événement de réclamation dans Kafka via le publisher.
     * Ne retourne PAS de ResponseEntity, laisse le contrôleur gérer HTTP.
     */
    public Mono<Void> submit(ComplaintDto payload) {
        log.debug("ComplaintsService.submit: build + enrich + publish");

        // Prépare les surcharges serveur
        java.util.Map<String, Object> overrides = new java.util.HashMap<>();
        overrides.put("SITE", "WEB");
        overrides.put("ZONE", "ONLINE");
        overrides.put("DEPARTEMENT", "Direction IT / Développement Applicatif");
        overrides.put("CANALUTILISE", "WEB");
        overrides.put("DEVISE", "USD");
        if (payload.getAWSTemplateFormatVersion() == null) overrides.put("AWSTemplateFormatVersion", "");
        if (payload.getMOTIFBCC() == null) overrides.put("MOTIFBCC", "Renforcement de la sécurité des identités");
        if (payload.getAVISMOTIVE() == null) overrides.put("AVISMOTIVE", "Ils ne sont pas utilisés dans des chaînes de connexion de production et sont gérés conformément aux politiques de sécurité.");
        overrides.put("Conditions", new java.util.HashMap<>());

        String clientId = payload.getNUMEROCLIENT();
        Mono<java.util.Map<String, Object>> detailMono = (clientId != null && clientId.matches("^\\d{8}$"))
                ? accountsService.getCustomerDetail(clientId).onErrorResume(e -> Mono.just(java.util.Map.of()))
                : Mono.just(java.util.Map.of());

        return detailMono.flatMap(detail -> {
            Object nomClient = detail.getOrDefault("customerName", detail.getOrDefault("customerFullName", detail.get("name")));
            if (nomClient instanceof String s && !s.isBlank()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> condMap = (java.util.Map<String, Object>) overrides.get("Conditions");
                condMap.put("NOMCLIENT", s);
            }
            Object agenceName = detail.getOrDefault("agencyName", detail.get("customerAgency"));
            if (agenceName instanceof String s && !s.isBlank()) overrides.put("AGENCECLIENT", s);
            Object manager = detail.getOrDefault("agencyManager", detail.getOrDefault("branchManager", detail.get("managerName")));
            if (manager instanceof String s && !s.isBlank() && payload.getGERANTAGENCE() == null) overrides.put("GERANTAGENCE", s);

            if (payload.getMONTANTCONVERTI() == null && payload.getMONTANT() != null) {
                Object amt = payload.getMONTANT();
                if (amt instanceof String str) {
                    String normalized = str.replaceAll("[\\s\u00A0\u202F]", "").replace(".", "").replace(',', '.');
                    overrides.put("MONTANTCONVERTI", normalized);
                } else if (amt instanceof Number num) {
                    overrides.put("MONTANTCONVERTI", String.valueOf(num.doubleValue()));
                } else {
                    overrides.put("MONTANTCONVERTI", amt.toString());
                }
            }

            java.util.Map<String, Object> finalPayload = payloadBuilderService.buildWithOverrides(payload, overrides);
            return eventPublisherService.publish(finalPayload);
        });
    }
}
