package com.rawbank.reclamations.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class MotifBccResolver {
    private static final Logger log = LoggerFactory.getLogger(MotifBccResolver.class);
    private final Map<String, String> mapping = new HashMap<>();

    public MotifBccResolver() {
        loadFromJson();
        loadFromProperties();
        // Lecture test.txt désactivée pour ce cas d'usage actuel
        // loadFromTestTxt();
    }

    public String resolve(String type) {
        if (type == null) return null;
        return mapping.get(type.trim());
    }

    private void loadFromJson() {
        try (InputStream in = new ClassPathResource("motifs-bcc.json").getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, String> jsonMap = mapper.readValue(in, Map.class);
            if (jsonMap != null && !jsonMap.isEmpty()) {
                for (Map.Entry<String, String> e : jsonMap.entrySet()) {
                    String k = sanitize(e.getKey());
                    String v = sanitize(e.getValue());
                    if (!k.isEmpty() && !v.isEmpty()) mapping.put(k, v);
                }
            }
            log.info("MotifBCC (JSON) chargés: {}", mapping.size());
        } catch (IOException e) {
            log.info("motifs-bcc.json non trouvé en classpath: {}", e.getMessage());
        }
    }

    private void loadFromProperties() {
        Properties props = new Properties();
        try (InputStream in = new ClassPathResource("motifs-bcc.properties").getInputStream()) {
            props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            for (String name : props.stringPropertyNames()) {
                String value = props.getProperty(name);
                String k = sanitize(name);
                String v = sanitize(value);
                if (!k.isEmpty() && !v.isEmpty()) mapping.put(k, v);
            }
            log.info("MotifBCC (.properties) chargés: {}", mapping.size());
        } catch (IOException e) {
            log.info("motifs-bcc.properties non trouvé en classpath: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private void loadFromTestTxt() {
        // 1) Chemin absolu (workspace local)
        java.nio.file.Path testPath = java.nio.file.Paths.get("/Users/macbook/Documents/reclamations/test.txt");
        java.util.List<String> allLines = null;
        if (java.nio.file.Files.exists(testPath)) {
            try {
                allLines = java.nio.file.Files.readAllLines(testPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Lecture test.txt échouée (absolu): {}", e.getMessage());
            }
        } else {
            // 2) Fallback: classpath test.txt
            try (InputStream in = new ClassPathResource("test.txt").getInputStream()) {
                allLines = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines().toList();
            } catch (IOException e) {
                log.info("test.txt non trouvé (absolu ni classpath) – saut du chargement");
            }
        }

        if (allLines == null || allLines.isEmpty()) return;

        java.util.List<String> filtered = new java.util.ArrayList<>();
        for (String line : allLines) {
            if (line == null) continue;
            String l = sanitize(line);
            if (l.isEmpty()) continue;
            if (isIgnorableLine(l)) continue;
            filtered.add(l);
        }

        if ((filtered.size() % 2) != 0) {
            log.warn("test.txt: nombre de lignes filtrées impair ({}), certaines paires pourraient manquer.", filtered.size());
        }

        for (int i = 0; i + 1 < filtered.size(); i += 2) {
            String type = filtered.get(i);
            String motif = filtered.get(i + 1);
            if (!type.isEmpty() && !motif.isEmpty()) {
                mapping.put(type, motif);
            }
        }
        log.info("MotifBCC (test.txt) chargés, total mapping={}", mapping.size());
    }

    private boolean isIgnorableLine(String l) {
        if (l.startsWith("{") || l.startsWith("}")) return true;
        if (l.startsWith("\"") && (l.contains("username") || l.contains("password") || l.contains("token"))) return true;
        if (l.startsWith("http://") || l.startsWith("https://")) return true;
        if (l.equalsIgnoreCase("Domaines")) return true;
        if (l.equalsIgnoreCase("Motif BCC")) return true;
        if (l.startsWith("===")) return true;
        if (l.startsWith("Types")) return true;
        // Séparateurs numériques simples ("1.", "2.")
        if (l.matches("^\\d+\\.?$")) return true;
        return false;
    }

    private String sanitize(String s) {
        if (s == null) return "";
        String out = s
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\u2009', ' ')
                .replace('\u2013', '-') // tiret demi-cadratin
                .replace('\u2014', '-') // tiret cadratin
                .replace('\u2019', '\'') // apostrophe typographique
                .trim();
        // Normaliser espaces multiples
        out = out.replaceAll("\\s+", " ");
        return out;
    }
}