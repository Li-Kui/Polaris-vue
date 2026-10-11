package com.polaris.platform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.page.Page;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.platform.domain.PlatformMcpTool;
import com.polaris.platform.dto.McpCatalogRefreshResult;
import com.polaris.platform.dto.McpConnectionProbeResult;
import com.polaris.platform.dto.McpToolPolicyRequest;
import com.polaris.platform.dto.McpToolResponse;
import com.polaris.platform.mapper.PlatformMcpConnectionMapper;
import com.polaris.platform.mapper.PlatformMcpToolMapper;
import com.polaris.platform.mcp.McpConnectionProbe;
import com.polaris.platform.mcp.McpToolContractPolicy;
import com.polaris.platform.service.IPlatformMcpToolService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/** 网络操作不占数据库锁；目录、审批及连接变更统一先锁连接再改工具。 */
@Service
public class PlatformMcpToolServiceImpl implements IPlatformMcpToolService {
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};
    private final PlatformMcpConnectionMapper connections;
    private final PlatformMcpToolMapper tools;
    private final McpConnectionProbe probe;
    private final McpToolContractPolicy contracts;
    private final TransactionTemplate transactions;

    public PlatformMcpToolServiceImpl(PlatformMcpConnectionMapper connections, PlatformMcpToolMapper tools,
            McpConnectionProbe probe, McpToolContractPolicy contracts, PlatformTransactionManager transactionManager) {
        this.connections = connections;
        this.tools = tools;
        this.probe = probe;
        this.contracts = contracts;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Page<McpToolResponse> selectByConnection(Long connectionId, Integer pageNum, Integer pageSize, String catalogStatus) {
        Long tenantId = requireAdminTenant();
        if (catalogStatus != null && !catalogStatus.isBlank() && !Set.of("PRESENT", "MISSING", "STALE").contains(catalogStatus)) {
            throw new ServiceException("MCP工具目录状态无效");
        }
        return transactions.execute(status -> {
            PlatformMcpConnection connection = requireConnection(connections.lockById(tenantId, connectionId), tenantId);
            PageHelper.startPage(pageNum == null ? 1 : Math.max(1, pageNum), pageSize == null ? 20 : Math.max(1, Math.min(50, pageSize)));
            try {
                return Page.of(PageInfo.of(tools.selectByConnection(tenantId, connectionId, catalogStatus)),
                        tool -> McpToolResponse.from(tool, connection));
            } finally { PageHelper.clearPage(); }
        });
    }

    @Override
    public McpCatalogRefreshResult refresh(Long connectionId) {
        Long tenantId = requireAdminTenant();
        PlatformMcpConnection snapshot = requireConnection(connections.selectById(tenantId, connectionId), tenantId);
        if (!"0".equals(snapshot.getStatus())) throw new ServiceException("MCP连接已停用");
        List<PlatformMcpTool> discovered;
        try {
            McpConnectionProbeResult result = probe.probe(snapshot, true);
            discovered = prepare(result, snapshot);
        } catch (RuntimeException error) {
            recordFailure(snapshot);
            if (error instanceof ServiceException) throw error;
            throw new ServiceException("MCP工具发现失败，旧目录未变更");
        }
        return transactions.execute(status -> {
            PlatformMcpConnection current = requireConnection(connections.lockById(tenantId, connectionId), tenantId);
            if (!sameSnapshot(snapshot, current) || !"0".equals(current.getStatus())) throw stale();
            Map<String, PlatformMcpTool> previous = new LinkedHashMap<>();
            List<String> names = discovered.stream().map(PlatformMcpTool::getToolName).toList();
            if (!names.isEmpty()) tools.selectByNames(tenantId, connectionId, names)
                    .forEach(tool -> previous.put(tool.getToolName(), tool));
            int added = 0;
            int changed = 0;
            int unchanged = 0;
            String operator = CallerContextHolder.require().getUsername();
            for (PlatformMcpTool next : discovered) {
                PlatformMcpTool old = previous.remove(next.getToolName());
                if (old == null) {
                    next.setCreateBy(operator);
                    if (tools.insert(next) != 1) throw stale();
                    added++;
                } else if (Objects.equals(old.getStructureHash(), next.getStructureHash())
                        && Objects.equals(old.getConnectionEpoch(), current.getCatalogEpoch())
                        && "PRESENT".equals(old.getCatalogStatus())
                        && Objects.equals(old.getSchemaSupported(), next.getSchemaSupported())) {
                    // 不改结构/审批修订；管理员主动撤销的工具不会因刷新自动恢复。
                    if (tools.markSeen(tenantId, old.getId()) != 1) throw stale();
                    unchanged++;
                } else {
                    next.setId(old.getId());
                    next.setRevision(old.getRevision());
                    next.setUpdateBy(operator);
                    if (tools.update(next) != 1) throw stale();
                    changed++;
                }
            }
            int missing = tools.markMissing(tenantId, connectionId, names, operator);
            if (connections.recordDiscovery(tenantId, connectionId, true) != 1) throw stale();
            return new McpCatalogRefreshResult(current.getCatalogRevision() + 1,
                    discovered.size(), added, changed, missing, unchanged);
        });
    }

    @Override
    public McpToolResponse changePolicy(Long toolId, McpToolPolicyRequest request) {
        Long tenantId = requireAdminTenant();
        if (request == null || !Set.of("APPROVE", "REVOKE").contains(request.getAction() == null ? "" : request.getAction())
                || request.getRevision() == null || request.getConnectionRevision() == null
                || request.getCatalogRevision() == null || request.getStructureHash() == null
                || request.getReviewNote() != null && request.getReviewNote().length() > 500) {
            throw new ServiceException("MCP审核请求缺少快照版本、操作无效或备注过长");
        }
        PlatformMcpTool initial = requireTool(tools.selectById(tenantId, toolId), tenantId);
        return transactions.execute(status -> {
            PlatformMcpConnection connection = requireConnection(connections.lockById(tenantId, initial.getConnectionId()), tenantId);
            PlatformMcpTool tool = requireTool(tools.selectById(tenantId, toolId), tenantId);
            if (!Objects.equals(tool.getConnectionId(), connection.getId())
                    || !Objects.equals(tool.getRevision(), request.getRevision())
                    || !Objects.equals(tool.getStructureHash(), request.getStructureHash())
                    || !Objects.equals(connection.getRevision(), request.getConnectionRevision())
                    || !Objects.equals(connection.getCatalogRevision(), request.getCatalogRevision())) throw stale();
            resetReview(tool);
            if ("APPROVE".equals(request.getAction())) {
                if (!Boolean.TRUE.equals(request.getConfirmedReadOnly())) {
                    throw new ServiceException("须由管理员明确确认此工具仅执行查询操作，远端只读提示不能替代审核");
                }
                if (!"0".equals(connection.getStatus()) || !"PRESENT".equals(tool.getCatalogStatus())
                        || !Objects.equals(tool.getConnectionEpoch(), connection.getCatalogEpoch())) {
                    throw new ServiceException("MCP工具已失效或连接停用，请重新发现当前目录");
                }
                // 审核时再次执行本地支持策略，避免规则升级后旧 schema_supported 仍被信任。
                McpToolContractPolicy.Contract contract = contracts.prepare(new McpConnectionProbeResult.ToolPreview(
                        tool.getToolName(), tool.getToolTitle(), tool.getDescription(),
                        JSON.readValue(tool.getInputSchema(), JSON_OBJECT),
                        tool.getOutputSchema() == null ? null : JSON.readValue(tool.getOutputSchema(), JSON_OBJECT),
                        null, null, JSON.readValue(tool.getDefinitionJson(), JSON_OBJECT)));
                if (!contract.supported() || !"0".equals(tool.getSchemaSupported())
                        || !Objects.equals(contract.structureHash(), tool.getStructureHash())) {
                    throw new ServiceException("MCP工具Schema不受支持，不能审批启用");
                }
                tool.setReviewStatus("APPROVED");
                tool.setEnabled("0");
                tool.setClassification("READ_ONLY");
                tool.setReviewedRevision(tool.getRevision() + 1);
            } else {
                tool.setReviewStatus("REVOKED");
            }
            tool.setReviewedBy(CallerContextHolder.require().getUsername());
            tool.setReviewedTime(new Date());
            tool.setReviewNote(request.getReviewNote());
            tool.setUpdateBy(CallerContextHolder.require().getUsername());
            if (tools.update(tool) != 1) throw stale();
            tool.setRevision(tool.getRevision() + 1);
            return McpToolResponse.from(tool, connection);
        });
    }

    private List<PlatformMcpTool> prepare(McpConnectionProbeResult result, PlatformMcpConnection connection) {
        if (result == null || result.tools() == null || result.tools().size() > 200
                || !result.toolsSupported() && !result.tools().isEmpty()) {
            throw new ServiceException("MCP发现结果不完整或格式无效");
        }
        List<PlatformMcpTool> prepared = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (var preview : result.tools()) {
            var contract = contracts.prepare(preview);
            if (!names.add(preview.name())) throw new ServiceException("MCP工具发现存在重名");
            PlatformMcpTool tool = new PlatformMcpTool();
            tool.setTenantId(connection.getTenantId());
            tool.setConnectionId(connection.getId());
            tool.setToolName(preview.name());
            tool.setToolTitle(preview.title());
            tool.setDescription(preview.description());
            tool.setInputSchema(contract.inputSchema());
            tool.setOutputSchema(contract.outputSchema());
            tool.setDefinitionJson(contract.definitionJson());
            tool.setStructureHash(contract.structureHash());
            tool.setRevision(1L);
            tool.setConnectionEpoch(connection.getCatalogEpoch());
            tool.setCatalogStatus("PRESENT");
            tool.setSchemaSupported(contract.supported() ? "0" : "1");
            tool.setSchemaError(contract.schemaError());
            tool.setLastSeenTime(new Date());
            resetReview(tool);
            prepared.add(tool);
        }
        return prepared;
    }

    private void recordFailure(PlatformMcpConnection snapshot) {
        try {
            transactions.executeWithoutResult(status -> {
                PlatformMcpConnection current = connections.lockById(snapshot.getTenantId(), snapshot.getId());
                if (current != null && sameSnapshot(snapshot, current)) {
                    connections.recordDiscovery(snapshot.getTenantId(), snapshot.getId(), false);
                }
            });
        } catch (RuntimeException ignored) {
            // 失败状态记录不掩盖主错误；不记录远端异常原文。
        }
    }

    private boolean sameSnapshot(PlatformMcpConnection first, PlatformMcpConnection second) {
        return Objects.equals(first.getRevision(), second.getRevision())
                && Objects.equals(first.getCatalogRevision(), second.getCatalogRevision())
                && Objects.equals(first.getCatalogEpoch(), second.getCatalogEpoch());
    }

    private void resetReview(PlatformMcpTool tool) {
        tool.setReviewStatus("PENDING");
        tool.setEnabled("1");
        tool.setClassification(null);
        tool.setReviewedRevision(null);
        tool.setReviewedBy(null);
        tool.setReviewedTime(null);
        tool.setReviewNote(null);
    }

    private Long requireAdminTenant() {
        Long tenantId = PlatformTenantGuard.requireTenantId();
        if (!CallerContextHolder.require().isTenantAdmin()) throw new ServiceException("只有租户管理员可以管理MCP工具目录", 403);
        return tenantId;
    }

    private PlatformMcpConnection requireConnection(PlatformMcpConnection connection, Long tenantId) {
        if (connection == null || !Objects.equals(connection.getTenantId(), tenantId)) {
            throw new ServiceException("MCP连接不存在或无权访问");
        }
        return connection;
    }

    private PlatformMcpTool requireTool(PlatformMcpTool tool, Long tenantId) {
        if (tool == null || !Objects.equals(tool.getTenantId(), tenantId)) throw new ServiceException("MCP工具不存在或无权访问");
        return tool;
    }

    private ServiceException stale() { return new ServiceException("MCP连接或工具目录已变化，请刷新目录后重试"); }
}
