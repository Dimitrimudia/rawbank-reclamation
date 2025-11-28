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
        // DEVISE: provient du formulaire, sera validée/ajustée avec le compte sélectionné plus bas
        if (payload.getDEVISE() != null && !payload.getDEVISE().isBlank()) {
            overrides.put("DEVISE", payload.getDEVISE());
        }
        if (payload.getAWSTemplateFormatVersion() == null) overrides.put("AWSTemplateFormatVersion", "");
        if (payload.getMOTIFBCC() == null) overrides.put("MOTIFBCC", "Renforcement de la sécurité des identités");
        if (payload.getAVISMOTIVE() == null) overrides.put("AVISMOTIVE", "Ils ne sont pas utilisés dans des chaînes de connexion de production et sont gérés conformément aux politiques de sécurité.");
        // GERANTAGENCE côté surcharge (on prend la valeur fournie par le formulaire si présente)
        if (payload.getGERANTAGENCE() != null && !payload.getGERANTAGENCE().isBlank()) {
            overrides.put("GERANTAGENCE", payload.getGERANTAGENCE());
        }
        // MONTANTCONVERTI côté surcharge: priorité au champ fourni, sinon dérivé de MONTANT
        if (payload.getMONTANTCONVERTI() != null && !payload.getMONTANTCONVERTI().isBlank()) {
            overrides.put("MONTANTCONVERTI", payload.getMONTANTCONVERTI());
        } else if (payload.getMONTANT() != null) {
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
        overrides.put("Conditions", new java.util.HashMap<>());

        String clientId = payload.getNUMEROCLIENT();
        Mono<java.util.Map<String, Object>> detailMono = (clientId != null && clientId.matches("^\\d{8}$"))
                ? accountsService.getCustomerDetail(clientId).onErrorResume(e -> Mono.just(java.util.Map.of()))
                : Mono.just(java.util.Map.of());

        return detailMono.flatMap(detail -> {
            // NOMCLIENT doit provenir strictement de customerName
            Object nomClient = detail.get("customerName");
            if (nomClient instanceof String s && !s.isBlank()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> condMap = (java.util.Map<String, Object>) overrides.get("Conditions");
                condMap.put("NOMCLIENT", s);
            }
            // AGENCECLIENT doit provenir de agencyCode d'un des comptes dans accountList
            Object accountListObj = detail.get("accountList");
            if (accountListObj instanceof java.util.List<?> list && !list.isEmpty()) {
                for (Object o : list) {
                    if (o instanceof java.util.Map<?, ?> acc) {
                        Object agencyCode = acc.get("agencyCode");
                        if (agencyCode instanceof String s && !s.isBlank()) {
                            overrides.put("AGENCECLIENT", s);
                            // si on a COMPTESOURCE, tenter de faire matcher et en déduire la devise
                            String selected = payload.getCOMPTESOURCE();
                            Object accountNumber = acc.get("accountNumber");
                            Object suffix = acc.get("suffix");
                            if (selected != null && accountNumber instanceof String an && suffix instanceof String su) {
                                String composed = s + "-" + an + "-" + su;
                                if (selected.equals(composed)) {
                                    Object currencyCode = acc.get("currencyCode");
                                    String mapped = null;
                                    if (currencyCode instanceof Number n) {
                                        int code = n.intValue();
                                        mapped = switch (code) {
                                            case 181 -> "CDF";
                                            case 840 -> "USD";
                                            case 955 -> "EURO"; 
                                            case 826 -> "GBP";
                                            default -> null;
                                        };
                                    } else if (currencyCode instanceof String cs && !cs.isBlank()) {
                                        String csTrim = cs.trim();
                                        mapped = switch (csTrim) {
                                            case "181" -> "CDF";
                                            case "840" -> "USD";
                                            case "955" -> "EURO"; 
                                            case "826" -> "GBP";
                                            default -> null;
                                        };
                                    }
                                    if (mapped != null) {
                                        String formDevise = payload.getDEVISE();
                                        if (formDevise != null && !formDevise.equalsIgnoreCase(mapped)) {
                                            log.warn("Mismatch devise: formulaire='{}' vs compte='{}' (pris compte)", formDevise, mapped);
                                        }
                                        overrides.put("DEVISE", mapped);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
            // Ne pas enrichir GERANTAGENCE ni MONTANTCONVERTI depuis l'API externe: gérés côté surcharge ci-dessus

            java.util.Map<String, Object> finalPayload = payloadBuilderService.buildWithOverrides(payload, overrides);
            return eventPublisherService.publish(finalPayload);
        });
    }
}
