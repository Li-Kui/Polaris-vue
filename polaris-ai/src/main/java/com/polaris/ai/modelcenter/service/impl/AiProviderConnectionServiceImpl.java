package com.polaris.ai.modelcenter.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.domain.AiProviderConnection;
import com.polaris.ai.mapper.AiProviderConnectionMapper;
import com.polaris.ai.modelcenter.credential.CredentialAction;
import com.polaris.ai.modelcenter.credential.ProviderCredentialContext;
import com.polaris.ai.modelcenter.credential.ProviderCredentialService;
import com.polaris.ai.modelcenter.dto.ProviderConnectionCreateRequest;
import com.polaris.ai.modelcenter.dto.ProviderConnectionQuery;
import com.polaris.ai.modelcenter.dto.ProviderConnectionUpdateRequest;
import com.polaris.ai.modelcenter.protocol.ProtocolAdapterRegistry;
import com.polaris.ai.modelcenter.schema.ProviderProfileValueValidator;
import com.polaris.ai.modelcenter.schema.SchemaProfileDefinition;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.service.ProviderConnectionInternalService;
import com.polaris.ai.modelcenter.service.ProviderConnectionReferenceService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;
import com.polaris.ai.modelcenter.vo.ProviderConnectionVO;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/** Provider Connection 管理服务。 */
@Service
public class AiProviderConnectionServiceImpl implements IAiProviderConnectionService {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,49}$");
    private static final Set<String> STATUSES = Set.of("0", "1");
    private final AiProviderConnectionMapper connectionMapper;
    private final ProviderConnectionInternalService internalService;
    private final ProviderConnectionReferenceService referenceService;
    private final ProviderCredentialService credentialService;
    private final ProtocolAdapterRegistry protocolAdapterRegistry;
    private final ProviderProfileValueValidator profileValueValidator;

    public AiProviderConnectionServiceImpl(
            AiProviderConnectionMapper connectionMapper,
            ProviderConnectionInternalService internalService,
            ProviderConnectionReferenceService referenceService,
            ProviderCredentialService credentialService,
            ProtocolAdapterRegistry protocolAdapterRegistry,
            ProviderProfileValueValidator profileValueValidator) {
        this.connectionMapper = connectionMapper;
        this.internalService = internalService;
        this.referenceService = referenceService;
        this.credentialService = credentialService;
        this.protocolAdapterRegistry = protocolAdapterRegistry;
        this.profileValueValidator = profileValueValidator;
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderConnectionVO get(Long id) {
        AiProviderConnection connection = requireAccessible(id);
        return toView(connection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderConnectionVO> list(ProviderConnectionQuery query) {
        ProviderConnectionQuery condition = query == null
                ? new ProviderConnectionQuery() : query;
        AiProviderConnection entity = new AiProviderConnection();
        entity.setConnectionName(trimToNull(condition.getConnectionName()));
        entity.setProviderCode(normalizeOptionalCode(condition.getProviderCode()));
        entity.setProtocolCode(normalizeOptionalCode(condition.getProtocolCode()));
        entity.setNetworkMode(normalizeOptionalMode(condition.getNetworkMode()));
        entity.setStatus(normalizeOptionalStatus(condition.getStatus()));
        return connectionMapper.selectConnectionList(entity).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProviderConnectionCreateRequest request) {
        if (request == null) {
            throw new ServiceException("Provider Connection 请求不能为空");
        }
        String providerCode = normalizeCode(request.providerCode(), "Provider Code");
        String protocolCode = normalizeCode(request.protocolCode(), "Protocol Code");
        protocolAdapterRegistry.getRequired(protocolCode);
        SchemaProfileDefinition providerProfile = profileValueValidator.requireProvider(
                providerCode, protocolCode);
        String networkMode = normalizeMode(request.networkMode());
        String baseUrl = requireBaseUrl(request.baseUrl());
        Map<String, Object> credential = profileValueValidator.validateCredential(
                providerProfile, request.credential(), false);
        String extraConfig = profileValueValidator.serializeExtraConfig(
                profileValueValidator.validateExtraConfig(
                        providerProfile, request.extraConfig()));
        String operator = CallerUtils.getUsername();

        AiProviderConnection connection = new AiProviderConnection();
        connection.setTenantId(currentTenantId());
        connection.setDeptId(resolveCreateDeptId(request.deptId()));
        connection.setConnectionName(requireName(request.connectionName()));
        connection.setProviderCode(providerCode);
        connection.setProtocolCode(protocolCode);
        connection.setNetworkMode(networkMode);
        connection.setBaseUrl(baseUrl);
        connection.setExtraConfig(extraConfig);
        connection.setRevision(1L);
        connection.setStatus(normalizeStatus(request.status(), "1"));
        connection.setDelFlag("0");
        connection.setCreateBy(operator);
        connection.setUpdateBy(operator);
        connection.setCreateTime(new Date());
        connection.setUpdateTime(new Date());
        connection.setRemark(trimToNull(request.remark()));

        if (connectionMapper.insert(connection) != 1 || connection.getId() == null) {
            throw new ServiceException("创建 Provider Connection 失败");
        }
        if (!credential.isEmpty()) {
            String ciphertext = credentialService.encrypt(
                    credential, ProviderCredentialContext.from(connection));
            if (connectionMapper.updateCredentialAfterCreate(
                    connection.getId(), ciphertext, operator) != 1) {
                throw new ServiceException("保存 Provider Credential 失败");
            }
        }
        return connection.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProviderConnectionUpdateRequest request) {
        if (request == null) {
            throw new ServiceException("Provider Connection 请求不能为空");
        }
        AiProviderConnection current = requireAccessible(id);
        SchemaProfileDefinition providerProfile = profileValueValidator.requireProvider(
                current.getProviderCode(), current.getProtocolCode());
        CredentialAction action = request.credentialAction();
        if (action == null) {
            throw new ServiceException("Credential Action 不能为空");
        }
        long expectedRevision = requireRevision(request.expectedRevision());
        String ciphertext = switch (action) {
            case KEEP -> {
                requireCredentialAbsent(request.credential(), "KEEP");
                yield current.getCredentialCiphertext();
            }
            case CLEAR -> {
                requireCredentialAbsent(request.credential(), "CLEAR");
                yield null;
            }
            case REPLACE -> credentialService.encrypt(
                    profileValueValidator.validateCredential(
                            providerProfile, request.credential(), true),
                    ProviderCredentialContext.from(current));
        };
        int updated = connectionMapper.updateMutableFields(
                current.getId(), requireName(request.connectionName()), action.name(),
                ciphertext, trimToNull(request.remark()), CallerUtils.getUsername(),
                expectedRevision);
        requireUpdated(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiProviderConnection current = requireAccessible(id);
        referenceService.assertNotReferenced(id);
        int deleted = connectionMapper.deleteByRevision(
                current.getId(), CallerUtils.getUsername(), current.getRevision());
        requireUpdated(deleted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status, Long expectedRevision) {
        AiProviderConnection current = requireAccessible(id);
        int updated = connectionMapper.updateStatusByRevision(
                current.getId(), normalizeStatus(status, null), CallerUtils.getUsername(),
                requireRevision(expectedRevision));
        requireUpdated(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderConnectionRuntime getRuntime(Long id) {
        AiProviderConnection connection = internalService.findEnabledById(id);
        if (connection == null) {
            throw new ServiceException("Provider Connection 不存在、不可访问或未启用");
        }
        Map<String, Object> credentials = credentialService.decrypt(
                connection.getCredentialCiphertext(),
                ProviderCredentialContext.from(connection));
        SchemaProfileDefinition providerProfile = profileValueValidator.requireProvider(
                connection.getProviderCode(), connection.getProtocolCode());
        credentials = profileValueValidator.validateCredential(
                providerProfile, credentials, false);
        Map<String, Object> extraConfig = profileValueValidator.validateExtraConfig(
                providerProfile, parseExtraConfig(connection.getExtraConfig()));
        return new ProviderConnectionRuntime(
                connection.getId(), connection.getTenantId(), connection.getDeptId(),
                connection.getProviderCode(), connection.getProtocolCode(),
                connection.getNetworkMode(), connection.getBaseUrl(),
                extraConfig, revisionOf(connection),
                credentials);
    }

    private AiProviderConnection requireAccessible(Long id) {
        AiProviderConnection connection = internalService.findById(id);
        if (connection == null) {
            throw new ServiceException("Provider Connection 不存在或无权访问");
        }
        return connection;
    }

    private ProviderConnectionVO toView(AiProviderConnection connection) {
        return new ProviderConnectionVO(
                connection.getId(), connection.getTenantId(), connection.getDeptId(),
                connection.getConnectionName(), connection.getProviderCode(),
                connection.getProtocolCode(), connection.getNetworkMode(),
                connection.getBaseUrl(), parseExtraConfig(connection.getExtraConfig()),
                revisionOf(connection), connection.getStatus(),
                credentialService.isConfigured(connection.getCredentialCiphertext()),
                connection.getCreateBy(), connection.getCreateTime(),
                connection.getUpdateBy(), connection.getUpdateTime(),
                connection.getRemark());
    }

    private Map<String, Object> parseExtraConfig(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject object = JSON.parseObject(value);
            Map<String, Object> result = new LinkedHashMap<>();
            object.forEach(result::put);
            return Map.copyOf(result);
        } catch (Exception e) {
            throw new ServiceException("Provider Connection extraConfig 数据格式无效");
        }
    }

    private void requireCredentialAbsent(Map<String, Object> credential, String action) {
        if (credential != null && !credential.isEmpty()) {
            throw new ServiceException(action + " 时 Credential 必须为空");
        }
    }

    private Long currentTenantId() {
        if (!CallerUtils.isPlatformMode()) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(CallerUtils.getTenantId());
            if (tenantId <= 0) {
                throw new NumberFormatException();
            }
            return tenantId;
        } catch (Exception e) {
            throw new ServiceException("中台租户ID格式错误");
        }
    }

    private Long resolveCreateDeptId(Long requestedDeptId) {
        if (CallerUtils.isPlatformMode()) {
            return null;
        }
        if (CallerUtils.isSuperAdmin()) {
            return requestedDeptId;
        }
        return CallerUtils.getDeptId();
    }

    private String requireName(String value) {
        String name = trimToNull(value);
        if (name == null) {
            throw new ServiceException("Connection 名称不能为空");
        }
        if (name.length() > 100) {
            throw new ServiceException("Connection 名称不能超过 100 个字符");
        }
        return name;
    }

    private String requireBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException("Base URL 不能为空");
        }
        return value;
    }

    private String normalizeCode(String value, String label) {
        String code = trimToNull(value);
        if (code == null) {
            throw new ServiceException(label + " 不能为空");
        }
        code = code.toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ServiceException(label + " 格式无效");
        }
        return code;
    }

    private String normalizeOptionalCode(String value) {
        return value == null || value.isBlank() ? null : normalizeCode(value, "Code");
    }

    private String normalizeMode(String value) {
        String mode = trimToNull(value);
        if (mode == null) {
            throw new ServiceException("Network Mode 不能为空");
        }
        mode = mode.toUpperCase(Locale.ROOT);
        if (!Set.of("DIRECT", "RELAY", "PUBLIC", "INTERNAL").contains(mode)) {
            throw new ServiceException("连接方式仅支持直连或兼容中转");
        }
        return mode;
    }

    private String normalizeOptionalMode(String value) {
        return value == null || value.isBlank() ? null : normalizeMode(value);
    }

    private String normalizeStatus(String value, String defaultValue) {
        String status = trimToNull(value);
        if (status == null) {
            status = defaultValue;
        }
        if (status == null || !STATUSES.contains(status)) {
            throw new ServiceException("状态仅支持 1（启用）或 0（停用）");
        }
        return status;
    }

    private String normalizeOptionalStatus(String value) {
        return value == null || value.isBlank() ? null : normalizeStatus(value, null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long revisionOf(AiProviderConnection connection) {
        return connection.getRevision() == null ? 1L : connection.getRevision();
    }

    private long requireRevision(Long revision) {
        if (revision == null || revision < 1) {
            throw new ServiceException("expectedRevision 必须是正整数");
        }
        return revision;
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new ServiceException(
                    "PROVIDER_CONNECTION_CONFLICT：配置已被其他用户修改，请刷新后重试");
        }
    }
}
