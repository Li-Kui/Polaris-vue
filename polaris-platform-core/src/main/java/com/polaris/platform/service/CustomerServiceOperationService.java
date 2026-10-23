package com.polaris.platform.service;

import com.polaris.ai.customer.application.CustomerServiceCapability;
import com.polaris.ai.customer.application.CustomerServiceConfig;
import com.polaris.ai.workflow.application.WorkflowExecutionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowExecutionStartCommand;
import com.polaris.ai.workflow.application.WorkflowExecutionView;
import com.polaris.ai.workflow.contract.WorkflowExecutionStatus;
import com.polaris.ai.workflow.runtime.WorkflowInputValidator;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceOperation;
import com.polaris.platform.domain.CustomerServiceSession;
import com.polaris.platform.mapper.CustomerServiceMapper;
import com.polaris.platform.mapper.CustomerServiceOperationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** 仅无外部资源的登录态工作流测试；复用原执行队列，关联和入队在同一数据库事务提交。 */
@Service
public class CustomerServiceOperationService {
    private final CustomerServiceMapper customers;
    private final CustomerServiceOperationMapper operations;
    private final CustomerServiceAccess access;
    private final CustomerServiceConfigurationService configurations;
    private final CustomerServiceCapabilityService capabilities;
    private final WorkflowExecutionApplicationFacade workflows;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final CustomerServiceChannelService channels;
    private final WorkflowInputValidator inputValidator = new WorkflowInputValidator();

    public CustomerServiceOperationService(CustomerServiceMapper customers, CustomerServiceOperationMapper operations,
            CustomerServiceAccess access, CustomerServiceConfigurationService configurations,
            CustomerServiceCapabilityService capabilities, WorkflowExecutionApplicationFacade workflows,
            ObjectMapper json, PlatformTransactionManager transactionManager, CustomerServiceChannelService channels) {
        this.customers = customers;
        this.operations = operations;
        this.access = access;
        this.configurations = configurations;
        this.capabilities = capabilities;
        this.workflows = workflows;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.channels = channels;
    }

    public List<CapabilityView> capabilities(String sessionId) {
        var actor = access.requireAdmin();
        return read(() -> {
            var scope = scope(actor, sessionId, false);
            return scope.config().capabilities().stream().map(capability -> {
                try {
                    var contract = capabilities.testContract(capability);
                    return new CapabilityView(capability.code(), capability.name(), capability.description(),
                            contract.inputs().stream().filter(input -> "USER_INPUT".equals(capability.inputSources().get(input.name()))).toList(), true, null);
                } catch (ServiceException unavailable) {
                    return new CapabilityView(capability.code(), capability.name(), capability.description(), List.of(), false, "CAPABILITY_NOT_READY");
                }
            }).toList();
        });
    }

    public StartView start(String sessionId, String requestId, String capabilityCode, JsonNode input) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}") || capabilityCode == null
                || !capabilityCode.matches("[a-z][a-z0-9_]{0,31}") || input == null || !input.isObject()
                || input.size() > 10 || json.writeValueAsString(input).getBytes(StandardCharsets.UTF_8).length > 8192) throw invalid();
        var actor = access.requireAdmin();
        try {
            return transactions.execute(status -> {
                var scope = scope(actor, sessionId, true);
                var capability = capability(scope, capabilityCode);
                var contract = capabilities.testContract(capability);
                var built = buildInput(actor, capability, contract, input);
                var previous = operations.selectRequest(actor.tenantId(), sessionId, requestId);
                if (previous != null) {
                    if (!capabilityCode.equals(previous.getCapabilityCode()) || !built.missing().isEmpty()
                            || !json.readTree(built.json()).equals(json.readTree(previous.getInputJson()))) throw new ServiceException("INVALID_INPUT: 同一请求不能更换能力或参数", 409);
                    return new StartView("ACCEPTED", List.of(), view(previous, capability), null);
                }
                var issues = inputIssues(capability, contract, built);
                if (!issues.isEmpty()) return new StartView("INPUT_INVALID", built.missing(), null, "INPUT_CONSTRAINT_FAILED", issues);
                if (customers.countActiveSpeech(actor.tenantId(), sessionId, new Date()) > 0) throw new ServiceException("SPEECH_BUSY: 请先完成或取消语音识别", 409);
                if (customers.selectTurns(actor.tenantId(), sessionId).stream().anyMatch(turn -> "RUNNING".equals(turn.getStatus()))) {
                    throw new ServiceException("TURN_BUSY: 请先等待或停止当前需求识别", 409);
                }
                if (!built.missing().isEmpty()) return new StartView("INPUT_REQUIRED", built.missing(), null, "PARAMETERS_REQUIRED");
                if (operations.countSession(actor.tenantId(), sessionId) >= 50) throw new ServiceException("RATE_LIMITED: 每个会话最多 50 个测试任务", 429);
                for (var operation : operations.selectSession(actor.tenantId(), sessionId)) {
                    if (!terminal(execution(operation).status())) throw new ServiceException("CAPABILITY_BUSY: 当前测试任务尚未结束，请先刷新或取消", 409);
                }
                var operation = new CustomerServiceOperation();
                operation.setId(UUID.randomUUID().toString());
                operation.setTenantId(actor.tenantId());
                operation.setSessionId(sessionId);
                operation.setRequestId(requestId);
                operation.setCapabilityCode(capabilityCode);
                operation.setDefinitionId(capability.definitionId());
                operation.setWorkflowVersionId(capability.versionId());
                operation.setInputJson(built.json());
                operation.setCreateTime(new Date());
                WorkflowExecutionView execution;
                try {
                    execution = workflows.start(new WorkflowExecutionStartCommand(capability.definitionId(), capability.versionId(),
                            json.readTree(built.json()), "TEST", "customer-test:" + operation.getId(), null));
                } catch (ServiceException rejected) {
                    // 嵌套 REQUIRED 事务回滚后再返回未受理状态，不吞掉数据库或未知故障。
                    throw new ServiceException("CUSTOMER_WORKFLOW_START_REJECTED", 409);
                }
                operation.setExecutionId(execution.executionId());
                requireExecution(operation, execution);
                if (operations.insert(operation) != 1) throw new ServiceException("保存客服测试任务失败");
                return new StartView("ACCEPTED", List.of(), project(operation, capability, execution), null);
            });
        } catch (ServiceException rejected) {
            if ("CUSTOMER_WORKFLOW_START_REJECTED".equals(rejected.getMessage())) {
                return new StartView("REJECTED", List.of(), null, "WORKFLOW_START_REJECTED");
            }
            throw rejected;
        }
    }

    public List<OperationView> list(String sessionId) {
        var actor = access.requireAdmin();
        return read(() -> {
            var scope = scope(actor, sessionId, false);
            return operations.selectSession(actor.tenantId(), sessionId).stream()
                    .map(operation -> view(operation, capability(scope, operation.getCapabilityCode()))).toList();
        });
    }

    public OperationView get(String sessionId, String requestId) {
        var operation = find(sessionId, requestId);
        if (operation == null) throw missing();
        return operation;
    }

    /** 同一受控会话内可选核对关联，用于自然语言轮次与显式启动的去重。 */
    public OperationView find(String sessionId, String requestId) {
        var actor = access.requireAdmin();
        return read(() -> {
            var scope = scope(actor, sessionId, false);
            var operation = operations.selectRequest(actor.tenantId(), sessionId, requestId);
            return operation == null ? null : view(operation, capability(scope, operation.getCapabilityCode()));
        });
    }

    /** 只校验和补参，不入队；返回用户字段副本，不返回注入后的服务端身份。 */
    public PreparedInput prepare(String sessionId, String capabilityCode, JsonNode input) {
        if (input == null || !input.isObject() || input.size() > 10
                || json.writeValueAsString(input).getBytes(StandardCharsets.UTF_8).length > 8192) throw invalid();
        var actor = access.requireAdmin();
        return read(() -> {
            var capability = capability(scope(actor, sessionId, false), capabilityCode);
            var contract = capabilities.testContract(capability);
            var built = buildInput(actor, capability, contract, input);
            return new PreparedInput(capability.name(), input.deepCopy(), built.missing(), inputIssues(capability, contract, built),
                    contract.inputs().stream().filter(parameter -> "USER_INPUT".equals(capability.inputSources().get(parameter.name()))).toList());
        });
    }

    /** 纯读取可加入已有事务，预期的不可用结果不经嵌套模板将整个会话读取标为 rollback-only。 */
    private <T> T read(Supplier<T> action) {
        return TransactionSynchronizationManager.isActualTransactionActive() ? action.get() : transactions.execute(status -> action.get());
    }

    public void cancel(String sessionId, String requestId) {
        var actor = access.requireAdmin();
        transactions.executeWithoutResult(status -> {
            // 即使客服已停用或能力已撤销，仍允许真实所有者取消已经受理的纯测试任务。
            requireOwnedSession(actor, sessionId);
            var operation = operations.selectRequest(actor.tenantId(), sessionId, requestId);
            if (operation == null) throw missing();
            execution(operation);
            workflows.cancel(operation.getExecutionId());
        });
    }

    private Scope scope(CustomerServiceAccess.Actor actor, String sessionId, boolean active) {
        var session = requireOwnedSession(actor, sessionId);
        var app = customers.lockApp(actor.tenantId(), session.getAppId());
        if (app == null || !"ACTIVE".equals(app.getStatus()) || !Objects.equals(app.getRuntimeEpoch(), session.getRuntimeEpoch())
                || !session.getExpiresAt().after(new Date()) || active && !"ACTIVE".equals(session.getStatus())) {
            throw new ServiceException("SESSION_EXPIRED: 会话已关闭、过期或客服已停用", 409);
        }
        var release = customers.selectRelease(actor.tenantId(), session.getReleaseId());
        if (release == null || !session.getAppId().equals(release.getAppId())) throw missing();
        return new Scope(session, channels.restrict(session, configurations.config(release.getConfigJson())));
    }

    private CustomerServiceSession requireOwnedSession(CustomerServiceAccess.Actor actor, String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw missing();
        var session = customers.lockSession(actor.tenantId(), actor.userId(), id);
        if (session == null || !actor.tenantId().equals(session.getTenantId()) || !actor.userId().equals(session.getUserId())) throw missing();
        return session;
    }

    private CustomerServiceCapability capability(Scope scope, String code) {
        return scope.config().capabilities().stream().filter(value -> value.code().equals(code)).findFirst().orElseThrow(this::missing);
    }

    private BuiltInput buildInput(CustomerServiceAccess.Actor actor, CustomerServiceCapability capability,
            CustomerServiceCapabilityService.Contract contract, JsonNode input) {
        Set<String> allowed = new HashSet<>();
        var output = json.createObjectNode();
        List<String> missing = new ArrayList<>();
        for (var parameter : contract.inputs().stream().sorted(Comparator.comparing(CustomerServiceCapabilityService.InputParameter::name)).toList()) {
            String source = capability.inputSources().get(parameter.name());
            if ("PLATFORM_USER_ID".equals(source)) output.put(parameter.name(), actor.userId());
            else if ("TENANT_ID".equals(source)) output.put(parameter.name(), actor.tenantId());
            else {
                allowed.add(parameter.name());
                var value = input.get(parameter.name());
                if (value == null) { if (parameter.required()) missing.add(parameter.name()); continue; }
                boolean valid = switch (parameter.type()) {
                    case "string" -> value.isString() && value.asString().length() <= 2000;
                    case "integer" -> value.isIntegralNumber();
                    case "number" -> value.isNumber();
                    case "boolean" -> value.isBoolean();
                    default -> false;
                };
                if (!valid) throw invalid();
                output.set(parameter.name(), value.deepCopy());
            }
        }
        for (var field : input.properties()) if (!allowed.contains(field.getKey())) throw invalid();
        return new BuiltInput(json.writeValueAsString(output), List.copyOf(missing));
    }

    private OperationView view(CustomerServiceOperation operation, CustomerServiceCapability capability) {
        capabilities.testContract(capability);
        return project(operation, capability, execution(operation));
    }

    private List<InputIssue> inputIssues(CustomerServiceCapability capability, CustomerServiceCapabilityService.Contract contract, BuiltInput built) {
        var input = json.readTree(built.json());
        var schema = contract.inputSchema();
        var properties = schema == null ? null : schema.path("properties");
        List<InputIssue> issues = new ArrayList<>();
        for (var parameter : contract.inputs()) {
            var value = input.get(parameter.name());
            if (value == null) continue;
            JsonNode rule = properties == null ? null : properties.get(parameter.name());
            if (rule == null) {
                var fallback = json.createObjectNode().put("type", parameter.type());
                if (parameter.constraints() != null) parameter.constraints().properties().forEach(entry -> fallback.set(entry.getKey(), entry.getValue()));
                rule = fallback;
            }
            if (inputValidator.validate(rule, value).isEmpty()) continue;
            if (!"USER_INPUT".equals(capability.inputSources().get(parameter.name()))) {
                throw new ServiceException("CAPABILITY_CONTRACT_CHANGED: 服务端身份不符合固定输入契约，请检查能力配置", 409);
            }
            issues.add(new InputIssue(parameter.name(), parameter.label(), "不符合参数约束，请按说明修改。"));
        }
        if (issues.isEmpty() && built.missing().isEmpty() && schema != null && !inputValidator.validate(schema, input).isEmpty()) {
            issues.add(new InputIssue("_input", "参数组合", "不符合工作流输入契约，请检查参数组合。"));
        }
        return List.copyOf(issues);
    }

    private WorkflowExecutionView execution(CustomerServiceOperation operation) {
        var execution = workflows.get(operation.getExecutionId());
        requireExecution(operation, execution);
        return execution;
    }

    private void requireExecution(CustomerServiceOperation operation, WorkflowExecutionView execution) {
        if (execution == null || !operation.getExecutionId().equals(execution.executionId())
                || !operation.getDefinitionId().equals(execution.definitionId())
                || !operation.getWorkflowVersionId().equals(execution.workflowVersionId())) throw missing();
    }

    private OperationView project(CustomerServiceOperation operation, CustomerServiceCapability capability, WorkflowExecutionView execution) {
        String state = execution.status();
        JsonNode result = null;
        String error = null;
        if ("SUCCEEDED".equals(state)) {
            try {
                if (execution.outputJson() == null || execution.outputJson().length() > 65536) throw invalid();
                var raw = json.readTree(execution.outputJson());
                var selected = json.createObjectNode();
                if (!raw.isObject()) throw invalid();
                for (String field : capability.outputFields()) {
                    var value = raw.get(field);
                    if (value == null || !value.isValueNode() || value.isString() && value.asString().length() > 2000) throw invalid();
                    selected.set(field, value.deepCopy());
                }
                if (json.writeValueAsString(selected).getBytes(StandardCharsets.UTF_8).length > 8192) throw invalid();
                result = selected;
            } catch (RuntimeException invalidResult) { state = "RESULT_REJECTED"; error = "OUTPUT_NOT_ALLOWED"; }
        } else if ("FAILED".equals(state) || "REJECTED".equals(state)) error = "WORKFLOW_FAILED";
        return new OperationView(operation.getId(), operation.getRequestId(), capability.code(), capability.name(), state, result, error, operation.getCreateTime());
    }

    private boolean terminal(String state) {
        try { return WorkflowExecutionStatus.valueOf(state).isTerminal(); }
        catch (RuntimeException unknown) { return false; }
    }
    private ServiceException invalid() { return new ServiceException("INVALID_INPUT: 测试任务输入格式、类型或长度无效，不能指定服务端身份参数", 400); }
    private ServiceException missing() { return new ServiceException("测试任务、能力或会话不存在或无权访问", 404); }
    private record Scope(CustomerServiceSession session, CustomerServiceConfig config) { }
    private record BuiltInput(String json, List<String> missing) { }
    public record CapabilityView(String code, String name, String description, List<CustomerServiceCapabilityService.InputParameter> inputs,
                                 boolean available, String errorCode) { }
    public record InputIssue(String name, String label, String message) { }
    public record StartView(String state, List<String> missingFields, OperationView operation, String errorCode, List<InputIssue> issues) {
        public StartView(String state, List<String> missingFields, OperationView operation, String errorCode) { this(state, missingFields, operation, errorCode, List.of()); }
    }
    public record PreparedInput(String name, JsonNode input, List<String> missingFields, List<InputIssue> issues,
                                List<CustomerServiceCapabilityService.InputParameter> parameters) { }
    public record OperationView(String id, String requestId, String capabilityCode, String name, String status,
                                JsonNode result, String errorCode, Date createTime) { }
}
