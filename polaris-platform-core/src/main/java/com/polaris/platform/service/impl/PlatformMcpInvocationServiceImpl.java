package com.polaris.platform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.agent.collaboration.AgentCallControl;
import com.polaris.ai.agent.collaboration.AgentInvocationGuard;
import com.polaris.ai.agent.collaboration.BoundedAgentContracts;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.observability.AiObservability;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.spi.WorkflowNodeContext;
import com.polaris.common.core.page.Page;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.platform.domain.PlatformMcpInvocation;
import com.polaris.platform.domain.PlatformMcpTool;
import com.polaris.platform.dto.McpConnectionProbeResult;
import com.polaris.platform.dto.McpInvokeRequest;
import com.polaris.platform.dto.McpInvokeResult;
import com.polaris.platform.dto.McpToolResponse;
import com.polaris.platform.mapper.PlatformMcpConnectionMapper;
import com.polaris.platform.mapper.PlatformMcpInvocationMapper;
import com.polaris.platform.mapper.PlatformMcpToolMapper;
import com.polaris.platform.mcp.McpInvocationException;
import com.polaris.platform.mcp.McpInvocationException.Code;
import com.polaris.platform.mcp.McpInvocationLimiter;
import com.polaris.platform.mcp.McpToolContractPolicy;
import com.polaris.platform.mcp.McpToolExecutor;
import com.polaris.platform.service.IPlatformMcpInvocationService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import com.polaris.platform.workflow.PlatformMcpWorkflowResourceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** 审计先落库，再做网络请求；数据库锁不跨网络，不依据远端注解推断幂等。 */
@Service
public class PlatformMcpInvocationServiceImpl implements IPlatformMcpInvocationService {
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};
    private static final Set<String> STATUSES = Set.of("STARTED", "SUCCESS", "FAILED", "REJECTED", "TOOL_ERROR", "TIMEOUT", "CANCELLED");
    private final PlatformMcpConnectionMapper connections;
    private final PlatformMcpToolMapper tools;
    private final PlatformMcpInvocationMapper invocations;
    private final McpToolContractPolicy contracts;
    private final McpToolExecutor executor;
    private final McpInvocationLimiter limiter;
    private final TransactionTemplate transactions;
    private final PlatformMcpWorkflowResourceProvider workflowResources;
    private final WorkflowProperties workflowProperties;
    @Autowired(required = false)
    private AgentInvocationGuard agentGuard;

    public PlatformMcpInvocationServiceImpl(PlatformMcpConnectionMapper connections, PlatformMcpToolMapper tools,
            PlatformMcpInvocationMapper invocations, McpToolContractPolicy contracts, McpToolExecutor executor,
            McpInvocationLimiter limiter, PlatformTransactionManager transactionManager) {
        this(connections, tools, invocations, contracts, executor, limiter, transactionManager, null);
    }

    public PlatformMcpInvocationServiceImpl(PlatformMcpConnectionMapper connections, PlatformMcpToolMapper tools,
            PlatformMcpInvocationMapper invocations, McpToolContractPolicy contracts, McpToolExecutor executor,
            McpInvocationLimiter limiter, PlatformTransactionManager transactionManager,
            PlatformMcpWorkflowResourceProvider workflowResources) {
        this(connections, tools, invocations, contracts, executor, limiter, transactionManager,
                workflowResources, new WorkflowProperties());
    }

    @Autowired
    public PlatformMcpInvocationServiceImpl(PlatformMcpConnectionMapper connections, PlatformMcpToolMapper tools,
            PlatformMcpInvocationMapper invocations, McpToolContractPolicy contracts, McpToolExecutor executor,
            McpInvocationLimiter limiter, PlatformTransactionManager transactionManager,
            PlatformMcpWorkflowResourceProvider workflowResources, WorkflowProperties workflowProperties) {
        this.connections = connections;
        this.tools = tools;
        this.invocations = invocations;
        this.contracts = contracts;
        this.executor = executor;
        this.limiter = limiter;
        this.workflowResources = workflowResources;
        this.workflowProperties = workflowProperties;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public McpInvokeResult invoke(Long toolId, McpInvokeRequest request) {
        return invokeAs(requireAdmin(), toolId, request, null);
    }

    @Override
    public McpInvokeResult invokeWorkflow(Long toolId, McpInvokeRequest request, WorkflowNodeContext context) {
        if (context == null || context.attemptNo() != 1 || !validRunId(context.executionId())
                || !validRunId(context.nodeRunId()) || workflowResources == null) {
            throw new ServiceException("MCP工作流调用上下文无效或尝试已重复；未发起远端调用");
        }
        String denied = workflowProperties.nodeExecutionDeniedReason(
                "mcp_tool", "1.0", "WRITE", context.tenantId(), context.workflowCode());
        if (denied != null) throw new ServiceException(denied);
        CallerContext previous = CallerContextHolder.get();
        try {
            CallerContext actor = workflowResources.requireActor(context.tenantId(), context.principalType(), context.principalId());
            CallerContextHolder.set(actor);
            return invokeAs(context.tenantId(), toolId, request, context);
        } finally { CallerContextHolder.set(previous); }
    }

    private boolean validRunId(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,64}");
    }

    private McpInvokeResult invokeAs(Long tenantId, Long toolId, McpInvokeRequest request, WorkflowNodeContext context) {
        return invokeAs(tenantId, toolId, request, context, null, null);
    }

    /** 有类型的委托入口，不能伪造显式 MCP 节点上下文或通过管理接口调用。 */
    public McpInvokeResult invokeAgent(BoundedAgentContracts.ToolIntent intent, AgentCallControl control) {
        if (intent == null || control == null || agentGuard == null || workflowResources == null)
            throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.ACCESS_DENIED, BoundedAgentContracts.Dispatch.NOT_DISPATCHED);
        try { checkAgent(intent, control); }
        catch (BoundedAgentContracts.Failure safe) {
            throw new BoundedAgentContracts.Failure(safe.code(), BoundedAgentContracts.Dispatch.NOT_DISPATCHED);
        }
        catch (RuntimeException denied) {
            throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.ACCESS_DENIED, BoundedAgentContracts.Dispatch.NOT_DISPATCHED);
        }
        var previous = CallerContextHolder.get();
        try {
            var actor = workflowResources.requireActor(intent.context().tenantId(), "PLATFORM_USER", String.valueOf(intent.context().actorUserId()));
            CallerContextHolder.set(actor);
            McpInvokeRequest request = new McpInvokeRequest();
            request.setRevision(intent.tool().revision()); request.setConnectionRevision(intent.tool().connectionRevision());
            request.setCatalogRevision(intent.tool().catalogRevision()); request.setStructureHash(intent.tool().structureHash());
            request.setArguments(JSON.readTree(intent.argumentsJson()));
            return invokeAs(intent.context().tenantId(), intent.tool().toolId(), request, null, intent, control);
        } catch (BoundedAgentContracts.Failure safe) { throw safe; }
        catch (RuntimeException unavailable) {
            // invokeAs 返回前出现异常只能证明没有正常结果，不能推断远端未收到请求。
            throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.AUDIT_INCOMPLETE, BoundedAgentContracts.Dispatch.UNKNOWN);
        } finally { CallerContextHolder.set(previous); }
    }

    private void checkAgent(BoundedAgentContracts.ToolIntent intent, AgentCallControl control) {
        if (intent == null) return;
        control.check();
        agentGuard.checkReserved(intent);
        workflowResources.requireActor(intent.context().tenantId(), "PLATFORM_USER", String.valueOf(intent.context().actorUserId()));
    }

    private McpInvokeResult invokeAs(Long tenantId, Long toolId, McpInvokeRequest request, WorkflowNodeContext context,
            BoundedAgentContracts.ToolIntent agent, AgentCallControl control) {
        long started = System.nanoTime();
        Snapshot snapshot;
        try {
            snapshot = transactions.execute(status -> {
                checkAgent(agent, control);
                PlatformMcpTool initial = requireTool(tools.selectById(tenantId, toolId), tenantId);
                PlatformMcpConnection connection = requireConnection(connections.lockById(tenantId, initial.getConnectionId()), tenantId);
                PlatformMcpTool tool = requireTool(tools.selectById(tenantId, toolId), tenantId);
                PlatformMcpInvocation audit = audit(connection, tool, context);
                if (agent != null) {
                    if(agent.context().entry() instanceof com.polaris.ai.agent.collaboration.AgentEntryIdentity.Chat chat) {
                        audit.setSource("AGENT_CHAT");audit.setChatRequestId(chat.requestId());audit.setConversationId(chat.conversationId());
                    } else {
                        audit.setSource("AGENT_WORKFLOW"); audit.setExecutionId(agent.context().executionId());
                        audit.setNodeRunId(agent.context().nodeRunId()); audit.setAttemptNo(agent.context().attemptNo());
                    }
                    audit.setAgentRunId(agent.context().runId()); audit.setAgentCallNo(agent.callNo());
                }
                if (invocations.insert(audit) != 1) throw new IllegalStateException();
                return new Snapshot(connection, tool, audit);
            });
        } catch (BoundedAgentContracts.Failure safe) {
            throw new BoundedAgentContracts.Failure(safe.code(), BoundedAgentContracts.Dispatch.NOT_DISPATCHED);
        } catch (ServiceException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            // 审计表缺失或不可写时拒绝网络调用；不能先执行再补记录。
            if (agent != null) throw new BoundedAgentContracts.Failure(BoundedAgentContracts.Code.AUDIT_INCOMPLETE, BoundedAgentContracts.Dispatch.NOT_DISPATCHED);
            throw new ServiceException("MCP调用审计不可用或已有相同节点尝试，请确认MCP结构脚本已执行；未发起远端调用");
        }
        Code failure = null;
        McpToolExecutor.Result result = null;
        try (McpInvocationLimiter.Lease ignored = limiter.acquire(tenantId)) {
            checkWorkflow(context);
            checkAgent(agent, control);
            validateSnapshot(snapshot, request);
            recheckContract(snapshot.tool());
            JsonNode arguments = request.getArguments();
            if (arguments == null || !arguments.isObject() || !bounded(arguments, 0, new int[]{0})
                    || JSON.writeValueAsBytes(arguments).length > 64 * 1024) throw new McpInvocationException(Code.INPUT_INVALID);
            contracts.validateValue(snapshot.tool().getInputSchema(), JSON.convertValue(arguments, OBJECT), false);
            Runnable before = () -> {
                checkWorkflow(context);
                checkAgent(agent, control);
                beforeCall(snapshot, request);
            };
            result = agent == null ? executor.execute(snapshot.connection(), snapshot.tool(), arguments, before)
                    : executor.execute(snapshot.connection(), snapshot.tool(), arguments, before, control);
            if (result.isError()) failure = Code.TOOL_ERROR;
        } catch (BoundedAgentContracts.Failure safe) {
            failure = switch (safe.code()) {
                case CANCELLED -> Code.CANCELLED;
                case DEADLINE_EXCEEDED -> Code.TIMEOUT;
                default -> Code.POLICY_CHANGED;
            };
        } catch (McpInvocationException safe) {
            failure = safe.code();
        } catch (RuntimeException unexpected) {
            failure = Code.INTERNAL_ERROR;
        }
        PlatformMcpInvocation audit = snapshot.audit();
        audit.setStatus(status(failure));
        audit.setErrorCode(failure == null ? null : failure.name());
        audit.setFinishedTime(new Date());
        audit.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        boolean recorded = finish(audit);
        AiObservability.recordTool("mcp", audit.getDurationMs(), audit.getStatus(), failure == null ? "NONE" : failure.name());
        return new McpInvokeResult(audit.getInvocationId(), audit.getStatus(), audit.getDurationMs(), audit.getErrorCode(),
                failure == null ? "调用完成" : failure.message(), failure != null,
                result == null ? null : result.content(), result == null ? null : result.structuredContent(), recorded,
                "1".equals(audit.getCallDispatched()));
    }

    private void checkWorkflow(WorkflowNodeContext context) {
        if (context == null) return;
        if (!workflowProperties.isNodeExecutionAllowed(
                "mcp_tool", "1.0", "WRITE", context.tenantId(), context.workflowCode())) {
            throw new McpInvocationException(Code.POLICY_CHANGED);
        }
        if (Thread.currentThread().isInterrupted() || context.cancellation().isCancellationRequested()) {
            throw new McpInvocationException(Code.CANCELLED);
        }
        try { workflowResources.requireActor(context.tenantId(), context.principalType(), context.principalId()); }
        catch (RuntimeException denied) { throw new McpInvocationException(Code.POLICY_CHANGED); }
    }

    private void beforeCall(Snapshot snapshot, McpInvokeRequest request) {
        if (Thread.currentThread().isInterrupted()) throw new McpInvocationException(Code.CANCELLED);
        transactions.executeWithoutResult(status -> {
            Long tenantId = snapshot.audit().getTenantId();
            PlatformMcpConnection connection = connections.lockById(tenantId, snapshot.connection().getId());
            PlatformMcpTool tool = tools.selectById(tenantId, snapshot.tool().getId());
            if (connection == null || tool == null) throw new McpInvocationException(Code.POLICY_CHANGED);
            validateSnapshot(new Snapshot(connection, tool, snapshot.audit()), request);
            if (!Objects.equals(connection.getCatalogEpoch(), snapshot.connection().getCatalogEpoch())) {
                throw new McpInvocationException(Code.POLICY_CHANGED);
            }
            if (invocations.markDispatched(tenantId, snapshot.audit().getInvocationId()) != 1) throw new IllegalStateException();
        });
        // 事务成功返回后才允许执行器发起 tools/call；不依赖终态审计写入是否成功。
        snapshot.audit().setCallDispatched("1");
    }

    private void validateSnapshot(Snapshot snapshot, McpInvokeRequest request) {
        var connection = snapshot.connection();
        var tool = snapshot.tool();
        if (request == null || request.getRevision() == null || request.getConnectionRevision() == null
                || request.getCatalogRevision() == null || request.getStructureHash() == null
                || !Objects.equals(tool.getConnectionId(), connection.getId())
                || !Objects.equals(tool.getRevision(), request.getRevision())
                || !Objects.equals(tool.getStructureHash(), request.getStructureHash())
                || !Objects.equals(connection.getRevision(), request.getConnectionRevision())
                || !Objects.equals(connection.getCatalogRevision(), request.getCatalogRevision())
                || !McpToolResponse.from(tool, connection).policyReady()) throw new McpInvocationException(Code.POLICY_CHANGED);
    }

    private void recheckContract(PlatformMcpTool tool) {
        var contract = contracts.prepare(new McpConnectionProbeResult.ToolPreview(tool.getToolName(), tool.getToolTitle(),
                tool.getDescription(), JSON.readValue(tool.getInputSchema(), OBJECT),
                tool.getOutputSchema() == null ? null : JSON.readValue(tool.getOutputSchema(), OBJECT),
                null, null, JSON.readValue(tool.getDefinitionJson(), OBJECT)));
        if (!contract.supported() || !Objects.equals(contract.structureHash(), tool.getStructureHash())) {
            throw new McpInvocationException(Code.POLICY_CHANGED);
        }
    }

    private boolean bounded(JsonNode value, int depth, int[] count) {
        if (depth > 48 || ++count[0] > 10000) return false;
        if (value.isContainer()) for (JsonNode child : value) if (!bounded(child, depth + 1, count)) return false;
        return true;
    }

    private boolean finish(PlatformMcpInvocation audit) {
        // 底层取消保留线程中断；尝试记录终态后恢复，不把异常原文交给通用日志。
        boolean interrupted = Thread.interrupted();
        try { return Boolean.TRUE.equals(transactions.execute(status -> invocations.finish(audit) == 1)); }
        catch (RuntimeException ignored) { return false; }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private String status(Code code) {
        if (code == null) return "SUCCESS";
        return switch (code) {
            case TIMEOUT -> "TIMEOUT";
            case CANCELLED -> "CANCELLED";
            case TOOL_ERROR -> "TOOL_ERROR";
            case POLICY_CHANGED, DEFINITION_CHANGED, INPUT_INVALID, RATE_LIMITED -> "REJECTED";
            default -> "FAILED";
        };
    }

    private PlatformMcpInvocation audit(PlatformMcpConnection connection, PlatformMcpTool tool, WorkflowNodeContext context) {
        var actor = CallerContextHolder.require();
        PlatformMcpInvocation audit = new PlatformMcpInvocation();
        audit.setInvocationId(UUID.randomUUID().toString());
        audit.setTenantId(connection.getTenantId());
        audit.setActorUserId(actor.getUserId());
        audit.setActorName(actor.getUsername());
        audit.setSource(context == null ? "MANUAL" : "WORKFLOW");
        if (context != null) {
            audit.setExecutionId(context.executionId());
            audit.setNodeRunId(context.nodeRunId());
            audit.setAttemptNo(context.attemptNo());
        }
        audit.setConnectionId(connection.getId());
        audit.setToolId(tool.getId());
        audit.setConnectionRevision(connection.getRevision());
        audit.setCatalogRevision(connection.getCatalogRevision());
        audit.setConnectionEpoch(connection.getCatalogEpoch());
        audit.setToolRevision(tool.getRevision());
        audit.setStructureHash(tool.getStructureHash());
        audit.setStartedTime(new Date());
        return audit;
    }

    @Override
    public Page<PlatformMcpInvocation> selectList(Long connectionId, Long toolId, String status, Integer pageNum, Integer pageSize, String executionId, String nodeRunId) {
        Long tenantId = requireAdmin();
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw new ServiceException("调用记录状态无效");
        if (executionId != null && !validRunId(executionId)) throw new ServiceException("工作流执行 ID 无效");
        if (nodeRunId != null && (executionId == null || !validRunId(nodeRunId))) throw new ServiceException("节点尝试范围无效");
        PageHelper.startPage(pageNum == null ? 1 : Math.max(1, pageNum), pageSize == null ? 20 : Math.max(1, Math.min(50, pageSize)));
        try { return Page.of(PageInfo.of(invocations.selectList(tenantId, connectionId, toolId, status, executionId, nodeRunId))); }
        catch (RuntimeException unavailable) { throw new ServiceException("MCP审计查询失败，请确认10、11号SQL已执行"); }
        finally { PageHelper.clearPage(); }
    }

    private Long requireAdmin() {
        Long tenantId = PlatformTenantGuard.requireTenantId();
        if (!CallerContextHolder.require().isTenantAdmin()) throw new ServiceException("只有租户管理员可以试调用或查看MCP调用记录", 403);
        return tenantId;
    }

    private PlatformMcpTool requireTool(PlatformMcpTool tool, Long tenantId) {
        if (tool == null || !Objects.equals(tool.getTenantId(), tenantId)) throw new ServiceException("MCP工具不存在或无权访问");
        return tool;
    }

    private PlatformMcpConnection requireConnection(PlatformMcpConnection connection, Long tenantId) {
        if (connection == null || !Objects.equals(connection.getTenantId(), tenantId)) throw new ServiceException("MCP连接不存在或无权访问");
        return connection;
    }

    private record Snapshot(PlatformMcpConnection connection, PlatformMcpTool tool, PlatformMcpInvocation audit) {}
}
