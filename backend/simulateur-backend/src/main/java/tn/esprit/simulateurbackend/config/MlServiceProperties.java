package tn.esprit.simulateurbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamification.ml-service")
public class MlServiceProperties {

    /**
     * Base URL of the Python FastAPI microservice
     * (example: http://localhost:5000).
     */
    private String baseUrl = "http://localhost:5000";

    private boolean enabled = true;

    private int connectTimeoutMs = 2000;

    private int readTimeoutMs = 3000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String predictUrl() {
        return rootUrl() + "/api/predict";
    }

    public String retrainUrl() {
        return rootUrl() + "/api/retrain";
    }

    public String healthUrl() {
        return rootUrl() + "/health";
    }

    public String rootUrl() {
        String root =
                baseUrl == null
                        ? "http://localhost:5000"
                        : baseUrl.trim();

        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }

        return root;
    }
}
