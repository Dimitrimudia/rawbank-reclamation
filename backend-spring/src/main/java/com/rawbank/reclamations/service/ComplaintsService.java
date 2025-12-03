package com.rawbank.reclamations.service;

import com.rawbank.reclamations.model.ComplaintDto;
import com.rawbank.reclamations.service.accounts.AccountsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Map;

@Service
public class ComplaintsService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintsService.class);

    private final MotifBccResolver motifBccResolver;
    private final SharepointListService sharepointListService;

    private final EventPublisherService eventPublisherService;
    private final PayloadBuilderService payloadBuilderService;
    private final AccountsService accountsService;
    private final SubmissionTrackingService submissionTrackingService;

    public ComplaintsService(EventPublisherService eventPublisherService,
                             PayloadBuilderService payloadBuilderService,
                             AccountsService accountsService,
                             MotifBccResolver motifBccResolver,
                             SubmissionTrackingService submissionTrackingService,
                             SharepointListService sharepointListService) {
        this.eventPublisherService = eventPublisherService;
        this.payloadBuilderService = payloadBuilderService;
        this.accountsService = accountsService;
        this.motifBccResolver = motifBccResolver;
        this.submissionTrackingService = submissionTrackingService;
        this.sharepointListService = sharepointListService;
    }

    /**
     * Orchestration métier: validation déjà assurée par le contrôleur.
     * Publie l'événement de réclamation dans Kafka via le publisher.
     * Ne retourne PAS de ResponseEntity, laisse le contrôleur gérer HTTP.
     */
    public Mono<String> submit(ComplaintDto payload) {
        log.debug("ComplaintsService.submit: build + enrich + publish");

        // Prépare les surcharges serveur
        java.util.Map<String, Object> overrides = new java.util.HashMap<>();
        overrides.put("SITE", "WEB");
        overrides.put("ZONE", "ONLINE");
        overrides.put("DEPARTEMENT", "Direction IT / Développement Applicatif");
        overrides.put("CANALUTILISE", "WEB");
        overrides.put("GERANTAGENCE", "");
        // DEVISE: provient du formulaire, sera validée/ajustée avec le compte sélectionné plus bas
        if (payload.getDEVISE() != null && !payload.getDEVISE().isBlank()) {
            overrides.put("DEVISE", payload.getDEVISE());
        }
        if (payload.getAWSTemplateFormatVersion() == null) overrides.put("AWSTemplateFormatVersion", "");
        // MOTIFBCC: toujours dérivé du TYPERECLAMATION via le resolver (repli sur le type si non mappé)
        String autoMotif = motifBccResolver.resolve(payload.getTYPERECLAMATION());
        if (autoMotif != null && !autoMotif.isBlank()) {
            overrides.put("MOTIFBCC", autoMotif);
        } else if (payload.getTYPERECLAMATION() != null && !payload.getTYPERECLAMATION().isBlank()) {
            overrides.put("MOTIFBCC", payload.getTYPERECLAMATION());
        }

        if (payload.getAVISMOTIVE() == null) overrides.put("AVISMOTIVE", payload.getMOTIF());
        
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
        String phone = payload.getTELEPHONECLIENT();
        Mono<java.util.Map<String, Object>> detailMono;
        if (clientId != null && clientId.matches("^\\d{8}$")) {
            detailMono = accountsService.getCustomerDetail(clientId).onErrorResume(e -> Mono.just(java.util.Map.of()));
        } else if (phone != null && phone.replaceAll("\\D", "").matches("^\\d{10}$")) {
            detailMono = accountsService.getCustomerDetailByPhone(phone).onErrorResume(e -> Mono.just(java.util.Map.of()));
        } else {
            detailMono = Mono.just(java.util.Map.of());
        }

        return detailMono.flatMap(detail -> {
            String trackingId = java.util.UUID.randomUUID().toString();
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

            overrides.put("TRACKINGID", trackingId);
            java.util.Map<String, Object> finalPayload = payloadBuilderService.buildWithOverrides(payload, overrides);
            // Création synchrone de l'item dans SharePoint et récupération d'un identifiant immédiatement
            submissionTrackingService.markPending(trackingId);
            return sharepointListService.createItem(finalPayload)
                    .map(resp -> extractComplaintNumber(resp))
                    .flatMap(number -> {
                        if (number == null || number.isBlank()) {
                            return Mono.error(new IllegalStateException("Numéro de réclamation introuvable"));
                        }
                        // Enrichir l'événement avec le numéro pour éviter un 2e appel côté consumer
                        finalPayload.put("complaintNumber", number);
                        // Marquer complété côté suivi
                        submissionTrackingService.complete(trackingId, number);
                        // Publier l'événement métier enrichi (audit, indexation, etc.)
                        return eventPublisherService.publish(finalPayload).thenReturn(number);
                    });
        });
    }

    private String extractComplaintNumber(Map<String, Object> response) {
        if (response == null) return null;
        Object v;
        if ((v = response.get("complaintNumber")) instanceof String s1 && !s1.isBlank()) return s1;
        if ((v = response.get("numero")) instanceof String s2 && !s2.isBlank()) return s2;
        if ((v = response.get("NUMERO")) instanceof String s3 && !s3.isBlank()) return s3;
        if ((v = response.get("reference")) instanceof String s4 && !s4.isBlank()) return s4;
        if ((v = response.get("ticket")) instanceof String s5 && !s5.isBlank()) return s5;
        v = response.get("id");
        if (v instanceof String s && !s.isBlank()) return s;
        if (v instanceof Number n) return String.valueOf(n);
        return null;
    }

    public Mono<SubmissionTrackingService.Status> status(String trackingId) {
        return submissionTrackingService.get(trackingId);
    }
}
