package com.polaris.platform.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.common.core.domain.BaseEntity;
import com.polaris.platform.domain.PlatformApiConnector;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对调用方安全的 API 连接器响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "API 连接器安全响应")
public class ApiConnectorResponse extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    private Long id;
    private Long tenantId;
    private String connectorName;
    private String baseUrl;
    private String authType;
    private Boolean credentialConfigured;
    private Map<String, String> defaultHeaders;
    private JsonNode responseSchema;
    private Integer timeoutMs;
    private String status;

    public static ApiConnectorResponse from(PlatformApiConnector connector) {
        if (connector == null) {
            return null;
        }
        ApiConnectorResponse response = new ApiConnectorResponse();
        response.setId(connector.getId());
        response.setTenantId(connector.getTenantId());
        response.setConnectorName(connector.getConnectorName());
        response.setBaseUrl(connector.getBaseUrl());
        response.setAuthType(connector.getAuthType());
        response.setCredentialConfigured(!"NONE".equalsIgnoreCase(connector.getAuthType())
                && connector.getAuthConfig() != null
                && !connector.getAuthConfig().isBlank());
        response.setDefaultHeaders(safeHeaders(connector.getDefaultHeaders()));
        response.setResponseSchema(safeSchema(connector.getResponseSchema()));
        response.setTimeoutMs(connector.getTimeoutMs());
        response.setStatus(connector.getStatus());
        response.setCreateBy(connector.getCreateBy());
        response.setCreateTime(connector.getCreateTime());
        response.setUpdateBy(connector.getUpdateBy());
        response.setUpdateTime(connector.getUpdateTime());
        response.setRemark(connector.getRemark());
        return response;
    }

    private static JsonNode safeSchema(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonNode schema = OBJECT_MAPPER.readTree(value);
            return schema != null && schema.isObject() ? schema : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, String> safeHeaders(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        try {
            JSONObject headers = JSON.parseObject(value);
            headers.forEach((name, headerValue) -> {
                if (name != null
                        && !SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    result.put(name, headerValue == null ? "" : String.valueOf(headerValue));
                }
            });
        } catch (Exception ignored) {
            // 历史异常数据不向前端透出，避免把不可识别内容误当作安全 Header。
        }
        return result;
    }
}
