package com.polaris.platform.service.impl;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.connector.ConnectorCredentialCipher;
import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.platform.dto.McpConnectionProbeResult;
import com.polaris.platform.dto.McpConnectionSaveRequest;
import com.polaris.platform.mapper.PlatformMcpConnectionMapper;
import com.polaris.platform.mapper.PlatformMcpToolMapper;
import com.polaris.platform.mcp.McpConnectionPolicy;
import com.polaris.platform.mcp.McpConnectionProbe;
import com.polaris.platform.service.IPlatformMcpConnectionService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PlatformMcpConnectionServiceImpl implements IPlatformMcpConnectionService {
    private final PlatformMcpConnectionMapper mapper;
    private final ConnectorCredentialCipher cipher;
    private final McpConnectionPolicy policy;
    private final McpConnectionProbe probe;
    private final PlatformMcpToolMapper tools;

    public PlatformMcpConnectionServiceImpl(PlatformMcpConnectionMapper mapper,
            ConnectorCredentialCipher cipher, McpConnectionPolicy policy, McpConnectionProbe probe,
            PlatformMcpToolMapper tools) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.policy = policy;
        this.probe = probe;
        this.tools = tools;
    }

    @Override
    public List<PlatformMcpConnection> selectList(String connectionName, String status) {
        return mapper.selectList(requireAdminTenant(), connectionName, status);
    }

    @Override
    public PlatformMcpConnection selectById(Long id) {
        return find(requireAdminTenant(), id);
    }

    @Override
    public PlatformMcpConnection insert(McpConnectionSaveRequest request) {
        Long tenantId = requireAdminTenant();
        if (request == null || request.getId() != null || request.getRevision() != null) {
            throw new ServiceException("新增MCP连接不能指定ID或修订号");
        }
        PlatformMcpConnection connection = prepare(request, null, tenantId);
        connection.setRevision(1L);
        connection.setCreateBy(CallerContextHolder.require().getUsername());
        mapper.insert(connection);
        return find(tenantId, connection.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformMcpConnection update(McpConnectionSaveRequest request) {
        Long tenantId = requireAdminTenant();
        if (request == null || request.getRevision() == null) {
            throw new ServiceException("修改MCP连接必须携带修订号");
        }
        PlatformMcpConnection current = find(tenantId, request.getId());
        if (!Objects.equals(current.getRevision(), request.getRevision())) {
            throw stale();
        }
        PlatformMcpConnection connection = prepare(request, current, tenantId);
        connection.setId(current.getId());
        connection.setRevision(request.getRevision());
        connection.setUpdateBy(CallerContextHolder.require().getUsername());
        PlatformMcpConnection locked = mapper.lockById(tenantId, current.getId());
        if (locked == null || !Objects.equals(locked.getTenantId(), tenantId)
                || !Objects.equals(locked.getRevision(), current.getRevision())) {
            throw stale();
        }
        if (mapper.update(connection) != 1) {
            throw stale();
        }
        if (!Objects.equals(current.getEndpointUrl(), connection.getEndpointUrl())
                || !Objects.equals(current.getAuthType(), connection.getAuthType())
                || !Objects.equals(current.getCredentialHeaderName(), connection.getCredentialHeaderName())
                || !Objects.equals(current.getAuthConfig(), connection.getAuthConfig())) {
            if (mapper.invalidateCatalog(tenantId, current.getId()) != 1) throw stale();
            tools.invalidateByConnection(tenantId, current.getId());
        }
        return find(tenantId, connection.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id, Long revision) {
        Long tenantId = requireAdminTenant();
        PlatformMcpConnection current = mapper.lockById(tenantId, id);
        if (current == null || !Objects.equals(current.getTenantId(), tenantId)) {
            throw new ServiceException("MCP连接不存在或无权访问");
        }
        if (tools.countByConnection(tenantId, id) > 0) {
            throw new ServiceException("MCP连接已有工具目录，请停用连接，避免工具目录或历史引用悬空");
        }
        if (revision == null || mapper.deleteById(tenantId, id, revision) != 1) {
            throw stale();
        }
        return 1;
    }

    @Override
    public McpConnectionProbeResult probe(Long id, boolean discoverTools) {
        PlatformMcpConnection connection = find(requireAdminTenant(), id);
        if (!"0".equals(connection.getStatus())) {
            throw new ServiceException("MCP连接已停用");
        }
        return probe.probe(connection, discoverTools);
    }

    private PlatformMcpConnection prepare(McpConnectionSaveRequest request,
            PlatformMcpConnection current, Long tenantId) {
        PlatformMcpConnection connection = new PlatformMcpConnection();
        connection.setTenantId(tenantId);
        connection.setConnectionName(text(request.getConnectionName(), 100, true));
        connection.setEndpointUrl(text(request.getEndpointUrl(), 2048, true));
        connection.setAuthType(request.getAuthType() == null ? "NONE" : request.getAuthType());
        connection.setCredentialHeaderName(text(request.getCredentialHeaderName(), 100, false));
        connection.setTimeoutMs(request.getTimeoutMs() == null ? 15000 : request.getTimeoutMs());
        connection.setStatus(request.getStatus() == null ? "0" : request.getStatus());
        connection.setRemark(text(request.getRemark(), 500, false));
        connection.setEndpointUrl(policy.validate(connection).toString());
        String action = request.getCredentialAction();
        if (!Set.of("KEEP", "REPLACE", "CLEAR").contains(action == null ? "" : action)) {
            throw new ServiceException("请明确指定MCP凭证保留、替换或清除操作");
        }
        if (!"REPLACE".equals(action) && request.getSecret() != null && !request.getSecret().isEmpty()) {
            throw new ServiceException("只有替换操作可以提交MCP凭证");
        }
        if ("NONE".equals(connection.getAuthType())) {
            if (!"CLEAR".equals(action)) {
                throw new ServiceException("无认证模式必须清除凭证");
            }
        } else if ("REPLACE".equals(action)) {
            policy.validateSecret(request.getSecret());
            connection.setAuthConfig(cipher.encrypt(request.getSecret()));
        } else if ("KEEP".equals(action) && current != null
                && cipher.isEncrypted(current.getAuthConfig())
                && Objects.equals(current.getEndpointUrl(), connection.getEndpointUrl())
                && Objects.equals(current.getAuthType(), connection.getAuthType())
                && Objects.equals(current.getCredentialHeaderName(), connection.getCredentialHeaderName())) {
            connection.setAuthConfig(current.getAuthConfig());
        } else {
            throw new ServiceException("认证类型、端点或请求头变更后必须重新提供MCP凭证");
        }
        return connection;
    }

    private Long requireAdminTenant() {
        Long tenantId = PlatformTenantGuard.requireTenantId();
        if (!CallerContextHolder.require().isTenantAdmin()) {
            throw new ServiceException("只有租户管理员可以管理和测试MCP连接", 403);
        }
        return tenantId;
    }

    private PlatformMcpConnection find(Long tenantId, Long id) {
        PlatformMcpConnection connection = id == null ? null : mapper.selectById(tenantId, id);
        if (connection == null || !Objects.equals(tenantId, connection.getTenantId())) {
            throw new ServiceException("MCP连接不存在或无权访问");
        }
        return connection;
    }

    private String text(String value, int maxLength, boolean required) {
        String text = value == null ? null : value.trim();
        if (text != null && text.isEmpty()) {
            text = null;
        }
        if (required && text == null || text != null && text.length() > maxLength) {
            throw new ServiceException("MCP配置字段为空或超出长度限制");
        }
        return text;
    }

    private ServiceException stale() {
        return new ServiceException("MCP连接已被其他操作修改，请刷新后重试");
    }
}
