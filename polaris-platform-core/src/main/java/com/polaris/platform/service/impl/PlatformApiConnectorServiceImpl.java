package com.polaris.platform.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.workflow.spi.WorkflowResourceUsageInspector;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.connector.ApiConnectorExecutor;
import com.polaris.platform.connector.ConnectorCredentialCipher;
import com.polaris.platform.connector.ConnectorHttpSafetyPolicy;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.dto.ApiConnectorCredentialRequest;
import com.polaris.platform.dto.ApiConnectorSaveRequest;
import com.polaris.platform.mapper.PlatformApiConnectorMapper;
import com.polaris.platform.service.IPlatformApiConnectorService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 中台 API 连接器服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformApiConnectorServiceImpl implements IPlatformApiConnectorService {

    private static final Set<String> AUTH_TYPES = Set.of("NONE", "API_KEY", "BEARER");
    private static final Set<String> CREDENTIAL_ACTIONS = Set.of("KEEP", "REPLACE", "CLEAR");
    private static final Set<String> STATUSES = Set.of("0", "1");
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "host", "content-length", "connection", "transfer-encoding");
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}$");

    @Autowired
    private PlatformApiConnectorMapper platformApiConnectorMapper;

    @Autowired
    private ApiConnectorExecutor apiConnectorExecutor;

    @Autowired
    private ConnectorCredentialCipher credentialCipher;

    @Autowired
    private ConnectorHttpSafetyPolicy safetyPolicy;

    @Autowired(required = false)
    private List<WorkflowResourceUsageInspector> usageInspectors = List.of();

    @Override
    public List<PlatformApiConnector> selectConnectorListByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return platformApiConnectorMapper.selectByTenantId(tenantId);
    }

    @Override
    public List<PlatformApiConnector> selectConnectorList(PlatformApiConnector connector) {
        connector.setTenantId(PlatformTenantGuard.requireTenantId());
        return platformApiConnectorMapper.selectList(connector);
    }

    @Override
    public PlatformApiConnector selectConnectorById(Long id) {
        PlatformApiConnector connector = platformApiConnectorMapper.selectById(id);
        return connector != null && PlatformTenantGuard.belongsToCurrentTenant(connector.getTenantId())
                ? connector : null;
    }

    @Override
    public PlatformApiConnector insertConnector(ApiConnectorSaveRequest request, String operator) {
        PlatformApiConnector connector = new PlatformApiConnector();
        connector.setTenantId(PlatformTenantGuard.requireTenantId());
        applyCommonFields(connector, request, true);
        connector.setCreateBy(operator);
        connector.setUpdateBy(operator);
        applyCredential(connector, request, true);
        if (platformApiConnectorMapper.insert(connector) != 1) {
            throw new ServiceException("创建 API 连接器失败");
        }
        return platformApiConnectorMapper.selectById(connector.getId());
    }

    @Override
    public PlatformApiConnector updateConnector(ApiConnectorSaveRequest request, String operator) {
        if (request == null || request.getId() == null) {
            throw new ServiceException("连接器 ID 不能为空");
        }
        PlatformApiConnector current = selectConnectorById(request.getId());
        if (current == null) {
            throw new ServiceException("API 连接器不存在或无权访问");
        }
        PlatformApiConnector connector = new PlatformApiConnector();
        connector.setId(current.getId());
        connector.setTenantId(current.getTenantId());
        connector.setCreateBy(current.getCreateBy());
        connector.setCreateTime(current.getCreateTime());
        connector.setDefaultHeaders(current.getDefaultHeaders());
        connector.setStatus(current.getStatus());
        connector.setUpdateBy(operator);
        applyCommonFields(connector, request, false);
        connector.setAuthConfig(current.getAuthConfig());
        applyCredential(connector, request, false);
        if (platformApiConnectorMapper.update(connector) != 1) {
            throw new ServiceException("修改 API 连接器失败");
        }
        return platformApiConnectorMapper.selectById(connector.getId());
    }

    @Override
    public int deleteConnectorById(Long id) {
        PlatformApiConnector connector = selectConnectorById(id);
        if (connector == null) {
            return 0;
        }
        long usages = countUsages(connector);
        if (usages > 0) {
            throw new ServiceException("连接器正被 " + usages + " 个工作流资源绑定使用，请先解除绑定或停用连接器");
        }
        return platformApiConnectorMapper.deleteById(id);
    }

    @Override
    public long countConnectorUsages(Long id) {
        PlatformApiConnector connector = selectConnectorById(id);
        if (connector == null) {
            throw new ServiceException("API 连接器不存在或无权访问");
        }
        return countUsages(connector);
    }

    @Override
    public ResponseEntity<String> invokeConnector(Long id, String path, HttpMethod method, Object body,
                                                  Map<String, String> queryParams) {
        PlatformApiConnector connector = selectConnectorById(id);
        if (connector == null) {
            throw new RuntimeException("连接器不存在");
        }
        return apiConnectorExecutor.execute(connector, path, method, body, queryParams);
    }

    /**
     * 应用非敏感公共字段，并在服务端统一执行边界校验。
     */
    private void applyCommonFields(
            PlatformApiConnector connector,
            ApiConnectorSaveRequest request,
            boolean creating) {
        if (request == null) {
            throw new ServiceException("连接器请求不能为空");
        }
        String connectorName = trim(request.getConnectorName());
        String baseUrl = trim(request.getBaseUrl());
        if (connectorName == null) {
            throw new ServiceException("连接器名称不能为空");
        }
        if (connectorName.length() > 128) {
            throw new ServiceException("连接器名称不能超过 128 个字符");
        }
        if (baseUrl == null) {
            throw new ServiceException("Base URL 不能为空");
        }
        try {
            safetyPolicy.validate(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }

        String authType = normalize(request.getAuthType(), "NONE");
        if (!AUTH_TYPES.contains(authType)) {
            throw new ServiceException("认证类型仅支持 NONE、API_KEY 或 BEARER");
        }
        int timeoutMs = request.getTimeoutMs() == null ? 30_000 : request.getTimeoutMs();
        if (timeoutMs < 1_000 || timeoutMs > 120_000) {
            throw new ServiceException("超时时间必须在 1 秒到 120 秒之间");
        }

        connector.setConnectorName(connectorName);
        connector.setBaseUrl(baseUrl);
        connector.setAuthType(authType);
        if (creating || request.getDefaultHeaders() != null) {
            connector.setDefaultHeaders(serializeHeaders(request.getDefaultHeaders()));
        }
        connector.setTimeoutMs(timeoutMs);
        String status = trim(request.getStatus());
        if (status == null && creating) status = "0";
        if (status != null && !STATUSES.contains(status)) {
            throw new ServiceException("连接器状态仅支持 0（正常）或 1（停用）");
        }
        connector.setStatus(status);
        connector.setRemark(request.getRemark());
    }

    /**
     * 根据显式动作保留、替换或清除凭证，避免普通编辑误删已有密钥。
     */
    private void applyCredential(
            PlatformApiConnector connector,
            ApiConnectorSaveRequest request,
            boolean creating) {
        String authType = connector.getAuthType();
        String action = normalize(request.getCredentialAction(), creating ? "REPLACE" : "KEEP");
        if (!CREDENTIAL_ACTIONS.contains(action)) {
            throw new ServiceException("凭证处理方式无效");
        }
        if ("NONE".equals(authType)) {
            connector.setAuthConfig(null);
            return;
        }
        if ("CLEAR".equals(action)) {
            throw new ServiceException("需要认证的连接器不能清除凭证，请改为无需认证");
        }
        if ("KEEP".equals(action)) {
            if (connector.getAuthConfig() == null || connector.getAuthConfig().isBlank()) {
                throw new ServiceException("当前认证方式尚未配置凭证");
            }
            // 历史明文凭证在再次保存时自动升级为加密格式。
            if (!credentialCipher.isEncrypted(connector.getAuthConfig())) {
                connector.setAuthConfig(credentialCipher.encrypt(connector.getAuthConfig()));
            }
            validateStoredCredential(authType, connector.getAuthConfig());
            return;
        }

        ApiConnectorCredentialRequest credential = request.getCredential();
        String secret = credential == null ? null : trim(credential.getSecret());
        if (secret == null) {
            throw new ServiceException("认证凭证不能为空");
        }
        JSONObject authConfig = new JSONObject();
        if ("API_KEY".equals(authType)) {
            String headerName = trim(credential.getHeaderName());
            validateCredentialHeader(headerName, request.getDefaultHeaders());
            authConfig.put("headerName", headerName);
            authConfig.put("apiKey", secret);
        } else {
            authConfig.put("token", secret);
        }
        connector.setAuthConfig(credentialCipher.encrypt(authConfig.toJSONString()));
    }

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        JSONObject result = new JSONObject();
        headers.forEach((name, value) -> {
            String safeName = trim(name);
            if (safeName == null || !HEADER_NAME.matcher(safeName).matches()) {
                throw new ServiceException("默认请求头名称格式无效");
            }
            if (BLOCKED_HEADERS.contains(safeName.toLowerCase(Locale.ROOT))) {
                throw new ServiceException("默认请求头不能包含受控字段: " + safeName);
            }
            result.put(safeName, value == null ? "" : value);
        });
        return result.toJSONString();
    }

    private void validateCredentialHeader(String headerName, Map<String, String> defaultHeaders) {
        if (headerName == null || !HEADER_NAME.matcher(headerName).matches()) {
            throw new ServiceException("API Key Header 名称格式无效");
        }
        if (BLOCKED_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))) {
            throw new ServiceException("API Key 不能使用受控请求头: " + headerName);
        }
        if (defaultHeaders != null && defaultHeaders.keySet().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(name -> name.equalsIgnoreCase(headerName))) {
            throw new ServiceException("默认请求头不能覆盖 API Key Header");
        }
    }

    private void validateStoredCredential(String authType, String encryptedConfig) {
        try {
            JSONObject config = JSON.parseObject(credentialCipher.decrypt(encryptedConfig));
            if ("API_KEY".equals(authType)) {
                validateCredentialHeader(trim(config.getString("headerName")), null);
                if (trim(config.getString("apiKey")) == null) {
                    throw new ServiceException("API Key 凭证不完整");
                }
            } else if (trim(config.getString("token")) == null) {
                throw new ServiceException("Bearer Token 凭证不完整");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("认证凭证格式无效");
        }
    }

    private String normalize(String value, String defaultValue) {
        String result = trim(value);
        return result == null ? defaultValue : result.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private long countUsages(PlatformApiConnector connector) {
        return usageInspectors.stream()
                .mapToLong(inspector -> inspector.countActiveUsages(
                        connector.getTenantId(), "API_CONNECTOR", String.valueOf(connector.getId())))
                .sum();
    }
}
