package com.rawbank.reclamations.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Charge un fichier .env et injecte ses clés comme PropertySource Spring.
 * Priorité élevée afin de permettre la résolution des placeholders ${...} depuis .env.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, org.springframework.boot.SpringApplication application) {
        Map<String, Object> props = new HashMap<>();

        // .env local (backend-spring/.env)
        Dotenv local = Dotenv.configure().directory("./").ignoreIfMissing().load();
        // .env à la racine du workspace (../../)
        Dotenv root = Dotenv.configure().directory("../../").ignoreIfMissing().load();

        // Helper pour copier les entrées d'un Dotenv vers la map de propriétés
        java.util.function.Consumer<Dotenv> copy = d -> {
            if (d == null) return;
            for (DotenvEntry e : d.entries()) {
                String key = e.getKey();
                String val = e.getValue();
                if (key == null || val == null) continue;
                props.putIfAbsent(key, val); // conserver première occurrence (local > root ou inverse selon ordre d'appel)
                // Mapper certaines clés .env vers des propriétés Spring attendues
                mapKnownAliases(props, key, val);
            }
        };

        // Priorité: backend-spring/.env puis racine/.env
        copy.accept(local);
        copy.accept(root);

        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("dotenv", props));
        }
    }

    private void mapKnownAliases(Map<String, Object> props, String key, String val) {
        // SharePoint auth
        if ("SHAREPOINT_AUTH_URL".equalsIgnoreCase(key)) props.putIfAbsent("sharepoint.auth.url", val);
        if ("SHAREPOINT_USERNAME".equalsIgnoreCase(key)) props.putIfAbsent("sharepoint.auth.username", val);
        if ("SHAREPOINT_PASSWORD".equalsIgnoreCase(key)) props.putIfAbsent("sharepoint.auth.password", val);
        if ("SHAREPOINT_ACCOUNT_DETAILS_URL".equalsIgnoreCase(key)) props.putIfAbsent("accounts.details.url", val);

        // Accounts OAuth/token si utilisé
        if ("ACCOUNTS_TOKEN_URL".equalsIgnoreCase(key)) props.putIfAbsent("accounts.auth.tokenUrl", val);

        // Kafka
        if ("SPRING_KAFKA_BOOTSTRAP_SERVERS".equalsIgnoreCase(key)) props.putIfAbsent("spring.kafka.bootstrap-servers", val);
        if ("APP_KAFKA_TOPIC_COMPLAINTS_RAW".equalsIgnoreCase(key)) props.putIfAbsent("app.kafka.topics.complaints-raw", val);
        if ("SPRING_KAFKA_GROUP_POWERAUTOMATE".equalsIgnoreCase(key)) props.putIfAbsent("app.kafka.groups.powerautomate", val);
        if ("SPRING_KAFKA_GROUP_INDEXER".equalsIgnoreCase(key)) props.putIfAbsent("app.kafka.groups.indexer", val);
        if ("SPRING_KAFKA_GROUP_AUDIT".equalsIgnoreCase(key)) props.putIfAbsent("app.kafka.groups.audit", val);

        // Elasticsearch
        if ("ELASTICSEARCH_URL".equalsIgnoreCase(key)) props.putIfAbsent("elasticsearch.url", val);
        if ("ELASTICSEARCH_INDEX".equalsIgnoreCase(key)) props.putIfAbsent("elasticsearch.index", val);
        if ("ELASTICSEARCH_USERNAME".equalsIgnoreCase(key)) props.putIfAbsent("elasticsearch.username", val);
        if ("ELASTICSEARCH_PASSWORD".equalsIgnoreCase(key)) props.putIfAbsent("elasticsearch.password", val);

        // CORS
        if ("ALLOWED_ORIGINS".equalsIgnoreCase(key)) props.putIfAbsent("app.cors.allowed-origins", val);

        // Power Automate
        if ("POWERAUTOMATE_URL".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.url", val);
        if ("POWERAUTOMATE_APIKEYHEADERNAME".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.apiKeyHeaderName", val);
        if ("POWERAUTOMATE_APIKEY".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.apiKey", val);
        if ("POWERAUTOMATE_TOKEN_URL".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.tokenUrl", val);
        if ("POWERAUTOMATE_CLIENT_ID".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.clientId", val);
        if ("POWERAUTOMATE_CLIENT_SECRET".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.clientSecret", val);
        if ("POWERAUTOMATE_SCOPE".equalsIgnoreCase(key)) props.putIfAbsent("powerautomate.scope", val);

        // Test helpers
        if ("APP_TEST_FORCEERROR".equalsIgnoreCase(key)) props.putIfAbsent("app.test.forceError", val);
    }

    @Override
    public int getOrder() {
        // Avant la plupart des autres processeurs environnements
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
