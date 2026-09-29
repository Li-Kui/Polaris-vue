package com.polaris.ai.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 联网搜索工具配置；与模型服务商凭据相互独立。 */
@Component
@ConfigurationProperties(prefix = "ai.tools.web-search")
public class WebSearchProperties {

    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
