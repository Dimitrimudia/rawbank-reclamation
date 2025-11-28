package com.rawbank.reclamations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.rawbank.reclamations.model.ComplaintDto;

@Service
public class PayloadBuilderService {
    private final ObjectMapper objectMapper;
    private final Map<String, Object> defaults;

    public PayloadBuilderService(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        ClassPathResource cpr = new ClassPathResource("default-payload.json");
        if (!cpr.exists()) {
            this.defaults = Map.of();
        } else {
            this.defaults = objectMapper.readValue(cpr.getInputStream(), new TypeReference<Map<String, Object>>() {});
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> build(ComplaintDto dto) {
        Map<String, Object> base = deepCopy(defaults);
        if (dto != null) {
            Map<String, Object> incoming = new HashMap<>();
            // Map DTO fields -> payload keys
            putIfNotNull(incoming, "SITE", dto.getSITE());
            putIfNotNull(incoming, "ZONE", dto.getZONE());
            putIfNotNull(incoming, "DEPARTEMENT", dto.getDEPARTEMENT());
            putIfNotNull(incoming, "DOMAINE", dto.getDOMAINE());
            putIfNotNull(incoming, "AWSTemplateFormatVersion", dto.getAWSTemplateFormatVersion());
            putIfNotNull(incoming, "TYPERECLAMATION", dto.getTYPERECLAMATION());
            putIfNotNull(incoming, "CANALUTILISE", dto.getCANALUTILISE());
            putIfNotNull(incoming, "MOTIFBCC", dto.getMOTIFBCC());
            putIfNotNull(incoming, "AGENCECLIENT", dto.getAGENCECLIENT());
            putIfNotNull(incoming, "NUMEROCLIENT", dto.getNUMEROCLIENT());
            if (dto.getConditions() != null) incoming.put("Conditions", dto.getConditions());
            putIfNotNull(incoming, "TELEPHONECLIENT", dto.getTELEPHONECLIENT());
            putIfNotNull(incoming, "COMPTESOURCE", dto.getCOMPTESOURCE());
            putIfNotNull(incoming, "DATETRANSACTION", dto.getDATETRANSACTION());

            // Normalisation NUMEROCARTE
            if (dto.getNUMEROCARTE() != null) {
                incoming.put("NUMEROCARTE", dto.getNUMEROCARTE().replaceAll("\\s+", ""));
            }

            // Normalisation MONTANT
            Object amount = dto.getMONTANT();
            if (amount instanceof String str) {
                String normalized = str.replaceAll("[\\s\u00A0\u202F]", "").replace(".", "").replace(',', '.');
                try { incoming.put("MONTANT", Double.parseDouble(normalized)); }
                catch (NumberFormatException ignored) { incoming.put("MONTANT", str); }
            } else if (amount != null) {
                incoming.put("MONTANT", amount);
            }

            putIfNotNull(incoming, "MONTANTCONVERTI", dto.getMONTANTCONVERTI());
            putIfNotNull(incoming, "DEVISE", dto.getDEVISE());
            if (dto.getEXTOURNE() != null) incoming.put("EXTOURNE", dto.getEXTOURNE());
            putIfNotNull(incoming, "GERANTAGENCE", dto.getGERANTAGENCE());
            putIfNotNull(incoming, "MOTIF", dto.getMOTIF());
            putIfNotNull(incoming, "DESCRIPTION", dto.getDESCRIPTION());
            putIfNotNull(incoming, "AVISMOTIVE", dto.getAVISMOTIVE());

            // Merge avec defaults
            base.putAll(incoming);

            // Fusion Conditions si présentes des deux côtés
            Object inCond = incoming.get("Conditions");
            Object baseCond = defaults.get("Conditions");
            if (inCond instanceof Map && baseCond instanceof Map) {
                Map<String, Object> merged = new HashMap<>((Map<String, Object>) baseCond);
                merged.putAll((Map<String, Object>) inCond);
                base.put("Conditions", merged);
            }
        }
        return base;
    }

    /**
     * Construit le payload final à partir du DTO, puis applique des surcharges côté serveur.
     * Utile pour compléter/forcer des champs attendus par l'API externe que le client ne fournit pas.
     */
    public Map<String, Object> buildWithOverrides(ComplaintDto dto, Map<String, Object> serverOverrides) {
        Map<String, Object> payload = build(dto);
        if (serverOverrides != null && !serverOverrides.isEmpty()) {
            payload.putAll(serverOverrides);
            // Fusion spécifique pour objets imbriqués connus
            Object ovCond = serverOverrides.get("Conditions");
            Object payCond = payload.get("Conditions");
            if (ovCond instanceof Map && payCond instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payCondMap = new HashMap<>((Map<String, Object>) payCond);
                @SuppressWarnings("unchecked")
                Map<String, Object> ovCondMap = (Map<String, Object>) ovCond;
                payCondMap.putAll(ovCondMap);
                payload.put("Conditions", payCondMap);
            }
        }
        return payload;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private Map<String, Object> deepCopy(Map<String, Object> src) {
        return objectMapper.convertValue(src, new TypeReference<Map<String, Object>>() {});
    }
}
