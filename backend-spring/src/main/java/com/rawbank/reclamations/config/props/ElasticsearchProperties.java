package com.rawbank.reclamations.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {
    private String url;
    private String index;
    private String username;
    private String password;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getIndex() { return index; }
    public void setIndex(String index) { this.index = index; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
