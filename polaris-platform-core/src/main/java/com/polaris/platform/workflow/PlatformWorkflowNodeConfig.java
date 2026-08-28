package com.polaris.platform.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.application.WorkflowResourceOption;
import com.polaris.ai.workflow.spi.*;
import com.polaris.platform.connector.ApiConnectorExecutor;
import com.polaris.platform.connector.ConnectorCredentialCipher;
import com.polaris.platform.connector.ConnectorHttpSafetyPolicy;
import com.polaris.platform.connector.DatasourceConnectorExecutor;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.domain.PlatformDatasourceVersion;
import com.polaris.platform.mapper.PlatformApiConnectorMapper;
import com.polaris.platform.mapper.PlatformDatasourceMapper;
import com.polaris.platform.mapper.PlatformDatasourceVersionMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 接口连接器和只读数据源的工作流适配器。 */
@Configuration
public class PlatformWorkflowNodeConfig {

    @Bean
    public WorkflowResourceProvider workflowApiConnectorResourceProvider(
            PlatformApiConnectorMapper mapper,
            ConnectorHttpSafetyPolicy safetyPolicy,
            ConnectorCredentialCipher credentialCipher) {
        return new WorkflowResourceProvider() {
            @Override
            public String kind() {
                return "API_CONNECTOR";
            }

            @Override
            public List<String> validate(WorkflowResourceRequest request) {
                PlatformApiConnector connector = connector(request, mapper);
                if (connector == null) return List.of("API连接器不存在或不属于当前操作范围");
                if (!"0".equals(connector.getStatus())) return List.of("API连接器已停用");
                try {
                    safetyPolicy.validate(connector.getBaseUrl());
                } catch (IllegalArgumentException e) {
                    return List.of(e.getMessage());
                }
                List<String> credentialErrors = validateCredential(connector, credentialCipher);
                if (!credentialErrors.isEmpty()) return credentialErrors;
                return List.of();
            }

            @Override
            public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                PlatformApiConnector connector = connector(request, mapper);
                if (connector == null) throw new IllegalArgumentException("API连接器资源不存在");
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("name", connector.getConnectorName());
                attributes.put("baseUrl", connector.getBaseUrl());
                return new ResolvedWorkflowResource(
                        kind(), request.resourceKey(), request.resourceId(), 0,
                        attributes, connector);
            }

            @Override
            public List<WorkflowResourceOption> listAvailable(
                    WorkflowResourceCatalogRequest request) {
                return mapper.selectWorkflowResources(request.tenantId()).stream()
                        .map(connector -> {
                            List<String> errors = validate(resourceRequest(
                                    request, kind(), connector.getId()));
                            Map<String, Object> attributes = new LinkedHashMap<>();
                            put(attributes, "baseUrl", connector.getBaseUrl());
                            put(attributes, "authType", connector.getAuthType());
                            attributes.put("credentialConfigured",
                                    connector.getAuthConfig() != null
                                            && !connector.getAuthConfig().isBlank());
                            return option(kind(), connector.getId(),
                                    connector.getConnectorName(), connector.getRemark(),
                                    connector.getStatus(), errors,
                                    connector.getTenantId() == null, attributes);
                        })
                        .toList();
            }
        };
    }

    @Bean
    public WorkflowResourceProvider workflowDatasourceResourceProvider(
            PlatformDatasourceMapper mapper,
            PlatformDatasourceVersionMapper versionMapper) {
        return new WorkflowResourceProvider() {
            @Override
            public String kind() {
                return "DATASOURCE";
            }

            @Override
            public List<String> validate(WorkflowResourceRequest request) {
                PlatformDatasource datasource = datasource(request, mapper, versionMapper);
                if (datasource == null) return List.of("数据源不存在或不属于当前操作范围");
                if (!"0".equals(datasource.getStatus())) return List.of("数据源已停用");
                if (!"AVAILABLE".equals(datasource.getVerificationStatus())) {
                    return List.of("数据源连接尚未通过验证");
                }
                if (datasource.getPassword() == null || datasource.getPassword().isBlank()) {
                    return List.of("数据源密码尚未配置");
                }
                return List.of();
            }

            @Override
            public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                PlatformDatasource datasource = datasource(request, mapper, versionMapper);
                if (datasource == null) throw new IllegalArgumentException("数据源资源不存在");
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("name", datasource.getDsName());
                attributes.put("type", datasource.getDsType());
                put(attributes, "host", datasource.getHost());
                put(attributes, "databaseName", datasource.getDatabaseName());
                put(attributes, "configVersion", datasource.getConfigVersion());
                return new ResolvedWorkflowResource(
                        kind(), request.resourceKey(), request.resourceId(),
                        datasource.getConfigVersion() == null ? 0 : datasource.getConfigVersion(),
                        attributes, datasource);
            }

            @Override
            public List<WorkflowResourceOption> listAvailable(
                    WorkflowResourceCatalogRequest request) {
                return mapper.selectWorkflowResources(request.tenantId()).stream()
                        .map(datasource -> {
                            List<String> errors = validate(resourceRequest(
                                    request, kind(), datasource.getId()));
                            Map<String, Object> attributes = new LinkedHashMap<>();
                            put(attributes, "type", datasource.getDsType());
                            put(attributes, "host", datasource.getHost());
                            put(attributes, "port", datasource.getPort());
                            put(attributes, "databaseName", datasource.getDatabaseName());
                            put(attributes, "configVersion", datasource.getConfigVersion());
                            put(attributes, "verificationStatus", datasource.getVerificationStatus());
                            return option(kind(), datasource.getId(), datasource.getDsName(),
                                    datasource.getRemark(), datasource.getStatus(),
                                    errors, datasource.getTenantId() == null, attributes);
                        })
                        .toList();
            }
        };
    }

    @Bean
    public WorkflowNodeHandler httpGetWorkflowNodeHandler(
            ApiConnectorExecutor executor, ObjectMapper objectMapper) {
        return httpHandler("http_get", "HTTP GET", WorkflowSideEffect.READ,
                true, executor, objectMapper);
    }

    @Bean
    public WorkflowNodeHandler httpRequestWorkflowNodeHandler(
            ApiConnectorExecutor executor, ObjectMapper objectMapper) {
        return httpHandler("http_request", "HTTP 写请求", WorkflowSideEffect.WRITE,
                false, executor, objectMapper);
    }

    @Bean
    public WorkflowNodeHandler databaseQueryWorkflowNodeHandler(
            DatasourceConnectorExecutor executor, ObjectMapper objectMapper) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "database_query", "1.0", "数据库只读查询", "data",
                databaseSchema(),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.READ,
                Set.of("DATASOURCE"),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                PlatformDatasource datasource = (PlatformDatasource) firstResource(
                        context, "DATASOURCE").handle();
                String sql = context.config().path("sql").asText();
                int maxRows = context.config().path("maxRows").asInt(100);
                int queryTimeoutSeconds = context.config().path("queryTimeoutSeconds").asInt(10);
                Map<String, Object> parameters = context.input() != null
                        && context.input().isObject()
                        ? objectMapper.convertValue(context.input(), new TypeReference<>() { })
                        : Map.of();
                List<Map<String, Object>> rows = executor.executeQuery(
                        datasource, sql, parameters,
                        Math.max(1, Math.min(maxRows, 1000)), queryTimeoutSeconds);
                ObjectNode result = objectMapper.createObjectNode();
                result.put("rowCount", rows.size());
                result.set("rows", objectMapper.valueToTree(rows));
                return WorkflowNodeResult.success(result);
            }
        };
    }

    private WorkflowNodeHandler httpHandler(
            String type,
            String displayName,
            WorkflowSideEffect sideEffect,
            boolean getOnly,
            ApiConnectorExecutor executor,
            ObjectMapper objectMapper) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                type, "1.0", displayName, "integration",
                httpSchema(getOnly),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                sideEffect,
                Set.of("API_CONNECTOR"),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.IDEMPOTENCY_KEY,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                PlatformApiConnector connector = (PlatformApiConnector) firstResource(
                        context, "API_CONNECTOR").handle();
                String methodName = getOnly ? "GET"
                        : context.config().path("method").asText("POST").toUpperCase();
                if (!Set.of("POST", "PUT", "PATCH", "DELETE").contains(methodName)
                        && !getOnly) {
                    throw new IllegalArgumentException("HTTP写请求仅允许POST、PUT、PATCH或DELETE");
                }
                HttpMethod method = HttpMethod.valueOf(methodName);
                String path = context.config().path("path").asText("");
                Map<String, String> query = queryParams(context.input());
                Object body = getOnly ? null : context.input();
                ResponseEntity<String> response = executor.execute(
                        connector, path, method, body, query);
                ObjectNode result = objectMapper.createObjectNode();
                result.put("status", response.getStatusCode().value());
                String bodyValue = response.getBody();
                if (bodyValue != null) {
                    try {
                        result.set("body", objectMapper.readTree(bodyValue));
                    } catch (Exception ignored) {
                        result.put("body", bodyValue);
                    }
                }
                return new WorkflowNodeResult(
                        result, Map.of(), sideEffect == WorkflowSideEffect.WRITE
                        ? "COMMITTED" : "NONE");
            }
        };
    }

    private static PlatformApiConnector connector(
            WorkflowResourceRequest request, PlatformApiConnectorMapper mapper) {
        try {
            return mapper.selectWorkflowResource(
                    request.tenantId(), Long.parseLong(request.resourceId()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static WorkflowResourceRequest resourceRequest(
            WorkflowResourceCatalogRequest request, String kind, Long resourceId) {
        return new WorkflowResourceRequest(
                request.tenantId(), request.environment(), kind, "catalog",
                String.valueOf(resourceId), request.principalType(), request.principalId());
    }

    private static WorkflowResourceOption option(
            String kind,
            Long resourceId,
            String name,
            String description,
            String status,
            List<String> errors,
            boolean shared,
            Map<String, Object> attributes) {
        List<String> safeErrors = errors == null ? List.of() : errors;
        return new WorkflowResourceOption(
                kind, String.valueOf(resourceId), name, description, status,
                safeErrors.isEmpty(), safeErrors.isEmpty() ? null : String.join("；", safeErrors),
                shared, Map.copyOf(attributes));
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static PlatformDatasource datasource(
            WorkflowResourceRequest request,
            PlatformDatasourceMapper mapper,
            PlatformDatasourceVersionMapper versionMapper) {
        try {
            PlatformDatasource datasource = mapper.selectWorkflowResource(
                    request.tenantId(), Long.parseLong(request.resourceId()));
            if (datasource == null || request.resourceVersion() == null) {
                return datasource;
            }
            PlatformDatasourceVersion version = versionMapper.selectByDatasourceAndVersion(
                    datasource.getId(), request.resourceVersion());
            if (version == null) return null;
            applyVersion(datasource, version);
            return datasource;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void applyVersion(
            PlatformDatasource datasource, PlatformDatasourceVersion version) {
        datasource.setCurrentVersionId(version.getId());
        datasource.setConfigVersion(version.getVersionNo());
        datasource.setHost(version.getHost());
        datasource.setPort(version.getPort());
        datasource.setDatabaseName(version.getDatabaseName());
        datasource.setUsername(version.getUsername());
        datasource.setPassword(version.getPassword());
        datasource.setSslEnabled(version.getSslEnabled());
        datasource.setConnectTimeoutSeconds(version.getConnectTimeoutSeconds());
        datasource.setQueryTimeoutSeconds(version.getQueryTimeoutSeconds());
        datasource.setVerificationStatus(version.getVerificationStatus());
        datasource.setDatabaseProductName(version.getDatabaseProductName());
        datasource.setDatabaseProductVersion(version.getDatabaseProductVersion());
        datasource.setLastTestTime(version.getLastTestTime());
        datasource.setLastTestLatencyMs(version.getLastTestLatencyMs());
        datasource.setLastTestErrorCode(version.getLastTestErrorCode());
    }

    private static ResolvedWorkflowResource firstResource(
            WorkflowNodeContext context, String kind) {
        return context.resources().values().stream()
                .filter(resource -> kind.equals(resource.kind()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少资源: " + kind));
    }

    private static Map<String, String> queryParams(JsonNode input) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode query = input == null ? null : input.get("query");
        if (query != null && query.isObject()) {
            query.fields().forEachRemaining(field ->
                    result.put(field.getKey(), field.getValue().asText()));
        }
        return result;
    }

    private static ObjectNode httpSchema(boolean getOnly) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode method = properties.putObject("method")
                .put("type", "string")
                .put("title", "请求方法")
                .put("description", getOnly
                        ? "GET按只读请求执行"
                        : "POST、PUT、PATCH、DELETE按写请求安全策略执行");
        if (getOnly) {
            method.putArray("enum").add("GET");
        } else {
            method.putArray("enum").add("POST").add("PUT").add("PATCH").add("DELETE");
        }
        properties.putObject("path")
                .put("type", "string")
                .put("title", "接口路径")
                .put("description", "只填写Base URL后面的相对路径，建议以/开头")
                .put("minLength", 1)
                .put("maxLength", 2048);
        schema.putArray("required").add("path");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode databaseSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("sql").put("type", "string");
        properties.putObject("maxRows").put("type", "integer")
                .put("minimum", 1).put("maximum", 1000);
        properties.putObject("queryTimeoutSeconds").put("type", "integer")
                .put("minimum", 1).put("maximum", 30);
        schema.putArray("required").add("sql");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static List<String> validateCredential(
            PlatformApiConnector connector,
            ConnectorCredentialCipher credentialCipher) {
        String authType = connector.getAuthType() == null
                ? "NONE" : connector.getAuthType().toUpperCase();
        if ("NONE".equals(authType)) return List.of();
        if (!Set.of("API_KEY", "BEARER").contains(authType)) {
            return List.of("API连接器认证类型不受支持");
        }
        if (connector.getAuthConfig() == null || connector.getAuthConfig().isBlank()) {
            return List.of("API连接器认证凭证尚未配置");
        }
        try {
            JSONObject config = JSON.parseObject(
                    credentialCipher.decrypt(connector.getAuthConfig()));
            if ("API_KEY".equals(authType)
                    && (config.getString("headerName") == null
                    || config.getString("headerName").isBlank()
                    || config.getString("apiKey") == null
                    || config.getString("apiKey").isBlank())) {
                return List.of("API连接器 API Key 配置不完整");
            }
            if ("BEARER".equals(authType)
                    && (config.getString("token") == null
                    || config.getString("token").isBlank())) {
                return List.of("API连接器 Bearer Token 配置不完整");
            }
            return List.of();
        } catch (Exception e) {
            return List.of("API连接器认证凭证无法读取");
        }
    }
}
