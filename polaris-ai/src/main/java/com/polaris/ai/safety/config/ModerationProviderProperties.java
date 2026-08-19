package com.polaris.ai.safety.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Bindable provider settings. Credential values are deliberately excluded from toString. */
@ConfigurationProperties(prefix = "ai.moderation.provider")
public class ModerationProviderProperties {
    private String type = "none";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String endpoint = "green-cip.cn-shanghai.aliyuncs.com";
    private String service = "comment_detection_pro";
    private int timeoutMs = 1500;
    private BigDecimal estimatedCostPerCall = new BigDecimal("0.01");
    private int circuitFailureThreshold = 5;
    private long circuitOpenSeconds = 60;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public BigDecimal getEstimatedCostPerCall() { return estimatedCostPerCall; }
    public void setEstimatedCostPerCall(BigDecimal estimatedCostPerCall) {
        this.estimatedCostPerCall = estimatedCostPerCall;
    }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int circuitFailureThreshold) {
        this.circuitFailureThreshold = circuitFailureThreshold;
    }
    public long getCircuitOpenSeconds() { return circuitOpenSeconds; }
    public void setCircuitOpenSeconds(long circuitOpenSeconds) {
        this.circuitOpenSeconds = circuitOpenSeconds;
    }
}
