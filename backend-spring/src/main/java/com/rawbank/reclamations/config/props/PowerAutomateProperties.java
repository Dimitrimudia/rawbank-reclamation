package com.rawbank.reclamations.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "powerautomate")
public class PowerAutomateProperties {
    private String url;
    private String apiKeyHeaderName;
    private String apiKey;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKeyHeaderName() { return apiKeyHeaderName; }
    public void setApiKeyHeaderName(String apiKeyHeaderName) { this.apiKeyHeaderName = apiKeyHeaderName; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
