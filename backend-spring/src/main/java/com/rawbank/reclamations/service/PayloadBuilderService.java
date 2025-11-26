package com.rawbank.reclamations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
    public Map<String, Object> build(Map<String, Object> incoming) {
        Map<String, Object> base = deepCopy(defaults);
        if (incoming != null) {
            // Normalisations minimales côté serveur
            Object card = incoming.get("NUMEROCARTE");
            if (card instanceof String s) {
                incoming = new HashMap<>(incoming);
                incoming.put("NUMEROCARTE", s.replaceAll("\\s+", ""));
            }
            Object amount = incoming.get("MONTANT");
            if (amount instanceof String str) {
                String normalized = str.replaceAll("[\\s\u00A0\u202F]", "").replace(".", "").replace(',', '.');
                try {
                    double val = Double.parseDouble(normalized);
                    incoming.put("MONTANT", val);
                } catch (NumberFormatException ignored) { /* keep as-is */ }
            }
            // Merge superficiel (1er niveau). Pour objets imbriqués connus, on fusionne.
            base.putAll(incoming);

            // Fusion Conditions (si présent des deux côtés)
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

    private Map<String, Object> deepCopy(Map<String, Object> src) {
        return objectMapper.convertValue(src, new TypeReference<Map<String, Object>>() {});
    }
}
