package com.polaris.ai.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.core.context.*;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.compiler.WorkflowDefinitionCompiler;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.contract.WorkflowErrorCode;
import com.polaris.ai.workflow.definition.WorkflowCompilationResult;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.domain.WorkflowNodeTestAudit;
import com.polaris.ai.workflow.domain.WorkflowNodeTestRun;
import com.polaris.ai.workflow.mapper.WorkflowNodeTestRunMapper;
import com.polaris.ai.workflow.node.WorkflowWaitPolicy;
import com.polaris.ai.workflow.registry.WorkflowNodeRegistry;
import com.polaris.ai.workflow.runtime.*;
import com.polaris.ai.workflow.security.WorkflowDataRedactor;
import com.polaris.ai.workflow.spi.*;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 对已保存草稿中的单个无副作用或只读节点执行隔离试运行。 */
@Service
public class WorkflowNodeTestService implements WorkflowNodeTestApplicationFacade {

    private static final int MAX_INPUT_BYTES = 256 * 1024;
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_SCHEMA_SAMPLES = 20;
    private static final Set<String> ENGINE_CONTROL_NODES = Set.of(
            "approval", "sub_workflow", "loop");
    private static final Set<String> CHAIN_CONTROL_NODES = Set.of(
            "approval", "wait", "sub_workflow", "condition", "parallel",
            "join", "loop", "llm_classifier");

    private final WorkflowDefinitionApplicationFacade definitions;
    private final WorkflowNodeSchemaApplicationFacade nodeSchemas;
    private final WorkflowDefinitionCompiler compiler;
    private final WorkflowNodeRegistry nodeRegistry;
    private final WorkflowResourceResolver resourceResolver;
    private final WorkflowNodeTestRunMapper testRunMapper;
    private final WorkflowNodeTestPersistenceService persistenceService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ThreadPoolTaskExecutor nodeExecutor;
    private final ObjectMapper objectMapper;
    private final WorkflowDataRedactor dataRedactor;
    private final WorkflowNodeTestPayloadCipher payloadCipher;
    private final WorkflowProperties workflowProperties;
    private final WorkflowInputValidator schemaValidator = new WorkflowInputValidator();
    private final WorkflowExpressionEvaluator expressionEvaluator;
    private final Map<String, ActiveTest> activeTests = new ConcurrentHashMap<>();
    private final String runnerId;

    public WorkflowNodeTestService(
            WorkflowDefinitionApplicationFacade definitions,
            WorkflowNodeSchemaApplicationFacade nodeSchemas,
            WorkflowDefinitionCompiler compiler,
            WorkflowNodeRegistry nodeRegistry,
            WorkflowResourceResolver resourceResolver,
            WorkflowNodeTestRunMapper testRunMapper,
            WorkflowNodeTestPersistenceService persistenceService,
            @Qualifier("workflowTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
            @Qualifier("workflowNodeTaskExecutor") ThreadPoolTaskExecutor nodeExecutor,
            ObjectMapper objectMapper,
            WorkflowDataRedactor dataRedactor,
            WorkflowNodeTestPayloadCipher payloadCipher,
            WorkflowProperties workflowProperties) {
        this.definitions = definitions;
        this.nodeSchemas = nodeSchemas;
        this.compiler = compiler;
        this.nodeRegistry = nodeRegistry;
        this.resourceResolver = resourceResolver;
        this.testRunMapper = testRunMapper;
        this.persistenceService = persistenceService;
        this.taskExecutor = taskExecutor;
        this.nodeExecutor = nodeExecutor;
        this.objectMapper = objectMapper;
        this.dataRedactor = dataRedactor;
        this.payloadCipher = payloadCipher;
        this.workflowProperties = workflowProperties;
        this.expressionEvaluator = new WorkflowExpressionEvaluator(objectMapper);
        this.runnerId = ManagementFactory.getRuntimeMXBean().getName()
                + ":node-test:" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public WorkflowNodeTestResult create(
            Long definitionId, String nodeId, WorkflowNodeTestCommand command) {
        PreparedTest prepared = prepare(definitionId, nodeId, command);
        String testRunId = UUID.randomUUID().toString();
        WorkflowNodeTestRun record = queuedRecord(testRunId, prepared);
        persistenceService.create(record, auditRecord(record));
        dispatch(testRunId, prepared);
        return toResult(record);
    }

    /** 续租本实例任务，并领取新任务或已过期任务。 */
    @Scheduled(
            fixedDelayString = "${ai.workflow.node-test-poll-ms:5000}",
            initialDelayString = "${ai.workflow.node-test-poll-ms:5000}")
    public void pollNodeTests() {
        if (!workflowProperties.isEnabled() || !workflowProperties.isWorkerEnabled()) return;
        activeTests.forEach((testRunId, active) -> {
            if (testRunMapper.renewLease(
                    testRunId, runnerId, active.fencingToken,
                    workflowProperties.getLeaseSeconds()) != 1) {
                active.cancel();
            }
        });
        for (String testRunId : testRunMapper.selectClaimCandidates(
                workflowProperties.getWorkerBatchSize())) {
            dispatch(testRunId, null);
        }
    }

    @Override
    public WorkflowNodeTestResult get(String testRunId) {
        WorkflowNodeTestRun record = requireRecord(testRunId);
        requireAccess(record);
        return toResult(record);
    }

    @Override
    public WorkflowNodeTestResult cancel(String testRunId) {
        WorkflowNodeTestRun record = requireRecord(testRunId);
        requireAccess(record);
        persistenceService.cancel(testRunId);
        ActiveTest active = activeTests.get(testRunId);
        if (active != null) active.cancel();
        return toResult(requireRecord(testRunId));
    }

    @Override
    public WorkflowInferredSchemaResult inferSchema(String testRunId) {
        WorkflowNodeTestRun record = requireRecord(testRunId);
        requireAccess(record);
        if (!"SUCCEEDED".equals(record.getStatus())) {
            throw new ServiceException("只有成功的单节点试运行才能生成样本 Schema");
        }
        if (readTree(record.getOutputJson()) == null) {
            throw new ServiceException("该试运行没有可用于推导的输出");
        }
        List<WorkflowNodeTestRun> compatible =
                testRunMapper.selectCompatibleSuccessfulSamples(
                        record.getDefinitionId(), record.getNodeId(), record.getEnvironment(),
                        record.getPrincipalType(), record.getPrincipalId(),
                        record.getNodeConfigHash(), record.getSchemaSourceVersion(),
                        MAX_SCHEMA_SAMPLES);
        Map<String, JsonNode> samplesByRun = new LinkedHashMap<>();
        if (compatible != null) {
            compatible.forEach(sample -> {
                JsonNode output = readTree(sample.getOutputJson());
                if (output != null) samplesByRun.put(sample.getTestRunId(), output);
            });
        }
        samplesByRun.putIfAbsent(record.getTestRunId(), readTree(record.getOutputJson()));
        List<JsonNode> samples = new ArrayList<>(samplesByRun.values());
        WorkflowSampleSchemaInferer.Result inferred =
                new WorkflowSampleSchemaInferer(objectMapper).infer(samples);
        return new WorkflowInferredSchemaResult(
                record.getTestRunId(), record.getDefinitionId(), record.getDraftRevision(),
                record.getNodeId(), inferred.schema(), Instant.now().toString(), samples.size(),
                record.getNodeConfigHash(), record.getSchemaSourceVersion(),
                inferred.diagnostics());
    }

    /** 同步入口仅用于服务内测试，产品接口统一使用异步任务。 */
    public WorkflowNodeTestResult run(
            Long definitionId, String nodeId, WorkflowNodeTestCommand command) {
        PreparedTest prepared = prepare(definitionId, nodeId, command);
        return executePrepared(UUID.randomUUID().toString(), prepared, new AtomicBoolean(false));
    }

    private void dispatch(String testRunId, PreparedTest prepared) {
        if (activeTests.containsKey(testRunId)) return;
        if (testRunMapper.claimLease(
                testRunId, runnerId, workflowProperties.getLeaseSeconds()) != 1) return;
        WorkflowNodeTestRun claimed = testRunMapper.selectByTestRunId(testRunId);
        if (claimed == null || claimed.getFencingToken() == null) return;
        ActiveTest active = new ActiveTest(claimed.getFencingToken());
        ActiveTest existing = activeTests.putIfAbsent(testRunId, active);
        if (existing != null) {
            testRunMapper.releaseLease(
                    testRunId, runnerId, claimed.getFencingToken());
            return;
        }
        try {
            active.future = taskExecutor.submit(
                    () -> executeAsync(testRunId, claimed, prepared, active));
        } catch (RuntimeException e) {
            activeTests.remove(testRunId, active);
            testRunMapper.releaseLease(
                    testRunId, runnerId, claimed.getFencingToken());
        }
    }

    private void executeAsync(
            String testRunId, WorkflowNodeTestRun claimed,
            PreparedTest prepared, ActiveTest active) {
        try {
            WorkflowNodeTestResult result;
            try {
                PreparedTest executable = prepared == null
                        ? prepareRecovered(claimed) : prepared;
                result = executePrepared(testRunId, executable, active.cancelled);
            } catch (RuntimeException e) {
                persistTerminal(
                        claimed, active, "FAILED", readTree(claimed.getInputJson()),
                        null, Map.of(),
                        claimed.getSchemaSource(), claimed.getSchemaSourceVersion(),
                        readList(claimed.getSchemaDiagnosticsJson()),
                        WorkflowErrorCode.INTERNAL_ERROR.name(), safeMessage(e), 0L);
                return;
            }
            persistTerminal(
                    claimed, active, result.status(), result.input(), result.output(), result.usage(),
                    result.schemaSource(), result.schemaSourceVersion(),
                    result.schemaDiagnostics(), result.errorCode(),
                    result.errorMessage(), result.durationMs());
        } finally {
            activeTests.remove(testRunId, active);
        }
    }

    private void persistTerminal(
            WorkflowNodeTestRun claimed, ActiveTest active,
            String status, JsonNode input, JsonNode output, Map<String, Number> usage,
            String schemaSource, String schemaSourceVersion,
            List<String> schemaDiagnostics, String errorCode,
            String errorMessage, long durationMs) {
        Map<String, Number> safeUsage = normalizeUsage(usage);
        persistenceService.finish(
                claimed.getTestRunId(), runnerId, active.fencingToken,
                status, writeJson(input), writeJson(output), writeJson(safeUsage),
                schemaSource, schemaSourceVersion, writeJson(schemaDiagnostics),
                errorCode, errorMessage, durationMs, totalTokens(safeUsage),
                costAmount(safeUsage), claimed.getAttemptCount() == null
                        ? 0 : claimed.getAttemptCount());
    }

    private PreparedTest prepareRecovered(WorkflowNodeTestRun record) {
        String payload = payloadCipher.decrypt(record.getPayloadCiphertext());
        JsonNode envelope = readTree(payload);
        if (envelope == null) throw new ServiceException("试运行恢复载荷不存在或已损坏");
        boolean versioned = envelope.path("payloadVersion").asInt(0) == 1;
        JsonNode input = versioned ? envelope.get("input") : envelope;
        String mode = versioned ? envelope.path("mode").asText("NODE")
                : record.getTestMode() == null ? "NODE" : record.getTestMode();
        CallerContext previous = CallerContextHolder.get();
        try {
            CallerContextHolder.set(record.getTenantId() == null
                    ? SystemCallerContext.INSTANCE
                    : new TenantSystemCallerContext(record.getTenantId()));
            PreparedTest prepared = prepare(
                    record.getDefinitionId(), record.getNodeId(),
                    new WorkflowNodeTestCommand(
                            input, record.getEnvironment(), record.getTimeoutSeconds(), mode),
                    new PrincipalIdentity(record.getPrincipalType(), record.getPrincipalId()));
            if (!Objects.equals(record.getDraftRevision(), prepared.definition().draftRevision())
                    || !Objects.equals(record.getNodeType(), prepared.node().getType())
                    || !Objects.equals(record.getHandlerVersion(), prepared.node().getHandlerVersion())
                    || !Objects.equals(record.getNodeConfigHash(), nodeConfigHash(prepared.node()))
                    || !Objects.equals(record.getSchemaSourceVersion(),
                            schemaVersion(prepared.schema()))) {
                throw new ServiceException("工作流草稿或资源契约已变化，请重新创建单节点试运行");
            }
            return prepared;
        } finally {
            CallerContextHolder.clear();
            CallerContextHolder.set(previous);
        }
    }

    private PreparedTest prepare(
            Long definitionId, String nodeId, WorkflowNodeTestCommand command) {
        return prepare(definitionId, nodeId, command, null);
    }

    private PreparedTest prepare(
            Long definitionId, String nodeId, WorkflowNodeTestCommand command,
            PrincipalIdentity principalOverride) {
        if (definitionId == null || nodeId == null || nodeId.isBlank()) {
            throw new ServiceException("工作流ID和节点ID不能为空");
        }
        WorkflowDefinitionView definition = definitions.getDefinition(definitionId);
        WorkflowCompilationResult compilation = compiler.compile(
                definition.draftJson(), "node-test:" + definition.draftRevision());
        if (!compilation.isValid()) {
            String message = compilation.diagnostics().stream()
                    .findFirst().map(item -> item.message()).orElse("工作流草稿校验失败");
            throw new ServiceException("试运行前请先修复草稿: " + message);
        }
        WorkflowExecutionPlan.PlanNode node = compilation.plan().getNodes().stream()
                .filter(item -> nodeId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("试运行节点不存在"));
        WorkflowNodeHandler handler = nodeRegistry.findHandler(
                        node.getType(), node.getHandlerVersion())
                .orElseThrow(() -> new ServiceException("试运行节点处理器不可用"));
        String mode = normalizeMode(command == null ? null : command.mode());
        List<WorkflowExecutionPlan.PlanNode> chain = "UPSTREAM_CHAIN".equals(mode)
                ? chainNodes(compilation.plan(), nodeId) : List.of(node);
        for (WorkflowExecutionPlan.PlanNode chainNode : chain) {
            WorkflowNodeHandler chainHandler = nodeRegistry.findHandler(
                            chainNode.getType(), chainNode.getHandlerVersion())
                    .orElseThrow(() -> new ServiceException(
                            "链式试运行节点处理器不可用: " + chainNode.getId()));
            requireSafeNode(chainNode, chainHandler);
            if ("UPSTREAM_CHAIN".equals(mode)
                    && CHAIN_CONTROL_NODES.contains(chainNode.getType())) {
                throw new ServiceException("链式试运行不支持控制节点: " + chainNode.getId());
            }
        }

        String environment = normalizeEnvironment(command == null ? null : command.environment());
        int timeoutSeconds = timeoutSeconds(node, command);
        JsonNode input = WorkflowJsonPayload.toJsonNode(
                command == null ? null : command.input(), objectMapper);
        if (input == null) input = objectMapper.createObjectNode();
        ensureSize(input, MAX_INPUT_BYTES, "试运行输入不能超过256KB");

        Map<String, WorkflowResolvedNodeSchemaView> resolvedSchemas = new LinkedHashMap<>();
        nodeSchemas.resolve(definitionId, environment)
                .forEach(item -> resolvedSchemas.put(item.nodeId(), item));
        WorkflowResolvedNodeSchemaView schema = resolvedSchemas.get(nodeId);
        JsonNode inputSchema = "UPSTREAM_CHAIN".equals(mode)
                ? compilation.plan().getInputs()
                : schema == null ? node.getInputSchema() : schema.inputSchema();
        List<String> inputErrors = schemaValidator.validate(inputSchema, input, "$.input");
        if (!inputErrors.isEmpty()) {
            throw new ServiceException("试运行输入校验失败: "
                    + String.join("；", inputErrors.subList(0, Math.min(inputErrors.size(), 5))));
        }

        CallerContext caller = CallerUtils.getContext();
        String principalType = principalOverride == null
                ? principalType(caller) : principalOverride.type();
        String principalId = principalOverride == null
                ? principalId(caller) : principalOverride.id();
        WorkflowExecutionPlan isolatedPlan = new WorkflowExecutionPlan();
        isolatedPlan.setNodes(chain);
        WorkflowResourceResolver.Resolution resources = resourceResolver.resolve(
                isolatedPlan, definitionId, definition.tenantId(), environment,
                principalType, principalId);
        return new PreparedTest(
                definition, node, handler, environment, timeoutSeconds, input, schema,
                principalType, principalId, resources, compilation.plan(), chain,
                Map.copyOf(resolvedSchemas), mode);
    }

    private WorkflowNodeTestResult executePrepared(
            String testRunId, PreparedTest prepared, AtomicBoolean cancelled) {
        if ("UPSTREAM_CHAIN".equals(prepared.mode())) {
            return executeUpstreamChain(testRunId, prepared, cancelled);
        }
        WorkflowExecutionPlan.PlanNode node = prepared.node();
        WorkflowCancellation cancellation = () -> cancelled.get()
                || Thread.currentThread().isInterrupted();
        ToolCallAuditSummary toolAudit = new ToolCallAuditSummary();
        WorkflowNodeContext context = new WorkflowNodeContext(
                "node-test:" + testRunId, testRunId, 1,
                prepared.definition().tenantId(), prepared.principalType(),
                prepared.principalId(), prepared.input(), node.getConfig(),
                resourceResolver.forNode(node, prepared.resources().resources()), cancellation,
                toolAudit);
        long startedAt = System.nanoTime();
        Future<WorkflowNodeResult> future;
        try {
            future = nodeExecutor.submit(() -> executeForTest(prepared.handler(), context));
        } catch (RuntimeException e) {
            throw new ServiceException("试运行任务繁忙，请稍后重试");
        }
        try {
            WorkflowNodeResult result = future.get(
                    prepared.timeoutSeconds(), TimeUnit.SECONDS);
            if (cancelled.get()) {
                return cancelled(testRunId, prepared, elapsedMs(startedAt), toolAudit.usage());
            }
            JsonNode output = result == null ? null : result.output();
            try {
                ensureSize(output, MAX_OUTPUT_BYTES, "试运行输出超过1MB，请缩小返回范围");
            } catch (ServiceException e) {
                return failed(testRunId, prepared, WorkflowErrorCode.QUOTA_EXCEEDED.name(),
                        e.getMessage(), elapsedMs(startedAt), toolAudit.usage());
            }
            List<String> outputErrors = schemaValidator.validate(
                    prepared.schema() == null ? node.getOutputSchema()
                            : prepared.schema().outputSchema(),
                    output, "$.output");
            if (!outputErrors.isEmpty()) {
                return failed(testRunId, prepared,
                        WorkflowErrorCode.OUTPUT_SCHEMA_MISMATCH.name(),
                        "节点输出不符合有效 Schema: " + String.join("；",
                                outputErrors.subList(0, Math.min(outputErrors.size(), 5))),
                        elapsedMs(startedAt), toolAudit.usage());
            }
            return new WorkflowNodeTestResult(
                    testRunId, prepared.definition().id(),
                    prepared.definition().draftRevision(), node.getId(), node.getType(),
                    node.getHandlerVersion(), prepared.environment(), prepared.mode(), "SUCCEEDED",
                    node.getSideEffect(), dataRedactor.redact(prepared.input()),
                    dataRedactor.redact(output), withToolAudit(
                            result == null ? Map.of() : result.usage(), toolAudit),
                    schemaSource(prepared.schema()), schemaVersion(prepared.schema()),
                    schemaDiagnostics(prepared.schema()), null, null, elapsedMs(startedAt));
        } catch (TimeoutException e) {
            cancelled.set(true);
            future.cancel(true);
            return failed(testRunId, prepared, WorkflowErrorCode.NODE_TIMEOUT.name(),
                    "单节点试运行超时", elapsedMs(startedAt), toolAudit.usage());
        } catch (InterruptedException e) {
            cancelled.set(true);
            future.cancel(true);
            Thread.currentThread().interrupt();
            return cancelled(testRunId, prepared, elapsedMs(startedAt), toolAudit.usage());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof WorkflowWaitPolicy.WaitException waitException) {
                return failed(testRunId, prepared, waitException.code(),
                        waitException.getMessage(), elapsedMs(startedAt), toolAudit.usage());
            }
            return failed(testRunId, prepared, WorkflowErrorCode.INTERNAL_ERROR.name(),
                    safeMessage(e.getCause()), elapsedMs(startedAt), toolAudit.usage());
        }
    }

    private WorkflowNodeTestResult executeUpstreamChain(
            String testRunId, PreparedTest prepared, AtomicBoolean cancelled) {
        long startedAt = System.nanoTime();
        long deadline = System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(prepared.timeoutSeconds());
        ObjectNode contextRoot = objectMapper.createObjectNode();
        contextRoot.set("input", prepared.input());
        ObjectNode outputs = contextRoot.putObject("nodes");
        contextRoot.putObject("env").put("name", prepared.environment());
        contextRoot.with("env").put("production", "PROD".equals(prepared.environment()));
        ObjectNode execution = contextRoot.putObject("execution");
        execution.put("id", "node-test:" + testRunId);
        execution.put("workflowCode", prepared.definition().workflowCode());
        execution.put("workflowVersionId",
                "draft:" + prepared.definition().draftRevision());
        execution.put("draftRevision", prepared.definition().draftRevision());
        execution.put("principalType", prepared.principalType());
        contextRoot.putObject("loop").put("branchPath", "node-test");
        contextRoot.putObject("approval");
        Map<String, Number> usage = new LinkedHashMap<>();
        ToolCallAuditSummary toolAudit = new ToolCallAuditSummary();
        JsonNode finalInput = prepared.input();
        JsonNode finalOutput = null;
        Future<WorkflowNodeResult> activeFuture = null;
        try {
            for (int index = 0; index < prepared.chain().size(); index++) {
                if (cancelled.get()) {
                    return cancelledWithInput(
                            testRunId, prepared, finalInput, elapsedMs(startedAt),
                            withToolAudit(usage, toolAudit));
                }
                WorkflowExecutionPlan.PlanNode current = prepared.chain().get(index);
                finalInput = current.getInputMapping().isEmpty()
                        ? prepared.input()
                        : expressionEvaluator.evaluateBindings(
                                current.getInputMapping(), contextRoot);
                WorkflowResolvedNodeSchemaView currentSchema =
                        prepared.schemas().get(current.getId());
                JsonNode currentInputSchema = currentSchema == null
                        ? current.getInputSchema() : currentSchema.inputSchema();
                List<String> inputErrors = schemaValidator.validate(
                        currentInputSchema, finalInput, "$.nodes." + current.getId() + ".input");
                if (!inputErrors.isEmpty()) {
                    return failedWithInput(
                            testRunId, prepared, finalInput,
                            WorkflowErrorCode.DEFINITION_INVALID.name(),
                            "链式试运行输入不符合节点契约: " + String.join("；",
                                    inputErrors.subList(0, Math.min(5, inputErrors.size()))),
                            elapsedMs(startedAt), withToolAudit(usage, toolAudit));
                }
                WorkflowNodeHandler currentHandler = nodeRegistry.findHandler(
                                current.getType(), current.getHandlerVersion())
                        .orElseThrow(() -> new ServiceException(
                                "链式试运行节点处理器不可用: " + current.getId()));
                WorkflowCancellation cancellation = () -> cancelled.get()
                        || Thread.currentThread().isInterrupted();
                WorkflowNodeContext nodeContext = new WorkflowNodeContext(
                        "node-test:" + testRunId,
                        testRunId + ":" + current.getId(), 1,
                        prepared.definition().tenantId(), prepared.principalType(),
                        prepared.principalId(), finalInput, current.getConfig(),
                        resourceResolver.forNode(
                                current, prepared.resources().resources()), cancellation,
                        toolAudit);
                activeFuture = nodeExecutor.submit(() -> executeForTest(currentHandler, nodeContext));
                long remainingMs = deadline - System.currentTimeMillis();
                long nodeMs = TimeUnit.SECONDS.toMillis(
                        current.getTimeoutSeconds() == null ? 60 : current.getTimeoutSeconds());
                if (remainingMs <= 0) throw new TimeoutException();
                WorkflowNodeResult result = activeFuture.get(
                        Math.min(remainingMs, nodeMs), TimeUnit.MILLISECONDS);
                finalOutput = result == null ? null : result.output();
                ensureSize(finalOutput, MAX_OUTPUT_BYTES,
                        "链式试运行节点输出超过1MB: " + current.getId());
                JsonNode currentOutputSchema = currentSchema == null
                        ? current.getOutputSchema() : currentSchema.outputSchema();
                List<String> outputErrors = schemaValidator.validate(
                        currentOutputSchema, finalOutput,
                        "$.nodes." + current.getId() + ".output");
                if (!outputErrors.isEmpty()) {
                    return failedWithInput(
                            testRunId, prepared, finalInput,
                            WorkflowErrorCode.OUTPUT_SCHEMA_MISMATCH.name(),
                            "链式试运行输出不符合节点契约: " + String.join("；",
                                    outputErrors.subList(0, Math.min(5, outputErrors.size()))),
                            elapsedMs(startedAt), withToolAudit(usage, toolAudit));
                }
                if (result != null && result.usage() != null) {
                    result.usage().forEach((key, value) -> mergeUsage(usage, key, value));
                }
                outputs.putObject(current.getId()).set("output",
                        finalOutput == null
                                ? com.fasterxml.jackson.databind.node.NullNode.instance
                                : finalOutput);
            }
            return new WorkflowNodeTestResult(
                    testRunId, prepared.definition().id(),
                    prepared.definition().draftRevision(), prepared.node().getId(),
                    prepared.node().getType(), prepared.node().getHandlerVersion(),
                    prepared.environment(), prepared.mode(), "SUCCEEDED", prepared.node().getSideEffect(),
                    dataRedactor.redact(finalInput), dataRedactor.redact(finalOutput),
                    withToolAudit(usage, toolAudit),
                    schemaSource(prepared.schema()), schemaVersion(prepared.schema()),
                    schemaDiagnostics(prepared.schema()), null, null, elapsedMs(startedAt));
        } catch (TimeoutException e) {
            cancelled.set(true);
            if (activeFuture != null) activeFuture.cancel(true);
            return failedWithInput(
                    testRunId, prepared, finalInput,
                    WorkflowErrorCode.NODE_TIMEOUT.name(),
                    "链式试运行超过总超时时间", elapsedMs(startedAt),
                    withToolAudit(usage, toolAudit));
        } catch (InterruptedException e) {
            cancelled.set(true);
            if (activeFuture != null) activeFuture.cancel(true);
            Thread.currentThread().interrupt();
            return cancelledWithInput(
                    testRunId, prepared, finalInput, elapsedMs(startedAt),
                    withToolAudit(usage, toolAudit));
        } catch (ExecutionException e) {
            return failedWithInput(
                    testRunId, prepared, finalInput,
                    WorkflowErrorCode.INTERNAL_ERROR.name(), safeMessage(e.getCause()),
                    elapsedMs(startedAt), withToolAudit(usage, toolAudit));
        } catch (RuntimeException e) {
            return failedWithInput(
                    testRunId, prepared, finalInput,
                    WorkflowErrorCode.INTERNAL_ERROR.name(), safeMessage(e),
                    elapsedMs(startedAt), withToolAudit(usage, toolAudit));
        }
    }

    private void mergeUsage(Map<String, Number> usage, String key, Number value) {
        if (key == null || key.isBlank() || value == null) return;
        try {
            BigDecimal amount = new BigDecimal(value.toString());
            if (amount.signum() < 0) return;
            BigDecimal previous = usage.get(key) == null
                    ? BigDecimal.ZERO : new BigDecimal(usage.get(key).toString());
            usage.put(key, previous.add(amount));
        } catch (NumberFormatException ignored) {
            // 非数值用量不进入聚合结果。
        }
    }

    private Map<String, Number> withToolAudit(
            Map<String, Number> usage, ToolCallAuditSummary toolAudit) {
        Map<String, Number> result = new LinkedHashMap<>();
        if (usage != null) result.putAll(usage);
        if (toolAudit != null) result.putAll(toolAudit.usage());
        return Map.copyOf(result);
    }

    private static final class ToolCallAuditSummary
            implements WorkflowToolCallObserver {

        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger succeeded = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger blocked = new AtomicInteger();
        private final AtomicInteger truncated = new AtomicInteger();
        private final AtomicLong durationMs = new AtomicLong();

        @Override
        public void onEvent(Event event) {
            if (event == null) return;
            switch (event.status()) {
                case STARTED -> {
                    attempts.incrementAndGet();
                    calls.incrementAndGet();
                }
                case SUCCEEDED -> {
                    succeeded.incrementAndGet();
                    durationMs.addAndGet(event.durationMs());
                    if (event.resultTruncated()) truncated.incrementAndGet();
                }
                case FAILED -> {
                    failed.incrementAndGet();
                    durationMs.addAndGet(event.durationMs());
                }
                case BLOCKED -> {
                    attempts.incrementAndGet();
                    blocked.incrementAndGet();
                }
            }
        }

        private Map<String, Number> usage() {
            if (attempts.get() == 0) return Map.of();
            Map<String, Number> usage = new LinkedHashMap<>();
            usage.put("toolAttempts", attempts.get());
            usage.put("toolCalls", calls.get());
            usage.put("toolSucceeded", succeeded.get());
            usage.put("toolFailed", failed.get());
            usage.put("toolBlocked", blocked.get());
            usage.put("toolDurationMs", durationMs.get());
            usage.put("toolResultTruncated", truncated.get());
            return Map.copyOf(usage);
        }
    }

    private WorkflowNodeTestResult failedWithInput(
            String testRunId, PreparedTest prepared, JsonNode input,
            String errorCode, String errorMessage, long durationMs,
            Map<String, Number> usage) {
        WorkflowNodeTestResult failed = failed(
                testRunId, prepared, errorCode, errorMessage, durationMs, usage);
        return new WorkflowNodeTestResult(
                failed.testRunId(), failed.definitionId(), failed.draftRevision(),
                failed.nodeId(), failed.nodeType(), failed.handlerVersion(),
                failed.environment(), failed.mode(), failed.status(), failed.sideEffect(),
                dataRedactor.redact(input), failed.output(), failed.usage(),
                failed.schemaSource(), failed.schemaSourceVersion(),
                failed.schemaDiagnostics(), failed.errorCode(),
                failed.errorMessage(), failed.durationMs());
    }

    private WorkflowNodeTestResult cancelledWithInput(
            String testRunId, PreparedTest prepared, JsonNode input, long durationMs,
            Map<String, Number> usage) {
        WorkflowNodeTestResult cancelled = cancelled(
                testRunId, prepared, durationMs, usage);
        return new WorkflowNodeTestResult(
                cancelled.testRunId(), cancelled.definitionId(), cancelled.draftRevision(),
                cancelled.nodeId(), cancelled.nodeType(), cancelled.handlerVersion(),
                cancelled.environment(), cancelled.mode(), cancelled.status(), cancelled.sideEffect(),
                dataRedactor.redact(input), cancelled.output(), cancelled.usage(),
                cancelled.schemaSource(), cancelled.schemaSourceVersion(),
                cancelled.schemaDiagnostics(), cancelled.errorCode(),
                cancelled.errorMessage(), cancelled.durationMs());
    }

    private void requireSafeNode(
            WorkflowExecutionPlan.PlanNode node, WorkflowNodeHandler handler) {
        if ("WRITE".equals(node.getSideEffect())) {
            throw new ServiceException("写节点暂不允许单节点真实试运行");
        }
        if ("loop".equals(node.getType())) {
            throw new ServiceException("受控循环需要使用真实执行引擎测试完整流程");
        }
        if (ENGINE_CONTROL_NODES.contains(node.getType())) {
            throw new ServiceException("持久化控制节点暂不支持隔离试运行");
        }
        if ("DURABLE_INTERNAL".equals(node.getSideEffect())
                && (!(handler instanceof WorkflowNodePreviewer)
                || !handler.descriptor().capabilities()
                .contains(WorkflowNodeCapability.PREVIEWABLE))) {
            throw new ServiceException("该持久化节点没有提供安全预览能力");
        }
        if ("NONE".equals(node.getSideEffect())
                && !handler.descriptor().capabilities()
                .contains(WorkflowNodeCapability.MOCKABLE)) {
            throw new ServiceException("该节点未声明可安全模拟能力");
        }
        if (!handler.descriptor().capabilities()
                .contains(WorkflowNodeCapability.CANCELLABLE)) {
            throw new ServiceException("该节点未声明可取消能力，暂不允许隔离试运行");
        }
    }

    private WorkflowNodeResult executeForTest(
            WorkflowNodeHandler handler, WorkflowNodeContext context) throws Exception {
        if (handler.descriptor().sideEffect() == WorkflowSideEffect.DURABLE_INTERNAL) {
            if (!(handler instanceof WorkflowNodePreviewer previewer)) {
                throw new ServiceException("该持久化节点没有提供安全预览能力");
            }
            return previewer.preview(context);
        }
        return handler.execute(context);
    }

    private WorkflowNodeTestResult failed(
            String testRunId, PreparedTest prepared, String errorCode,
            String errorMessage, long durationMs) {
        return failed(testRunId, prepared, errorCode, errorMessage, durationMs, Map.of());
    }

    private WorkflowNodeTestResult failed(
            String testRunId, PreparedTest prepared, String errorCode,
            String errorMessage, long durationMs, Map<String, Number> usage) {
        WorkflowExecutionPlan.PlanNode node = prepared.node();
        return new WorkflowNodeTestResult(
                testRunId, prepared.definition().id(),
                prepared.definition().draftRevision(), node.getId(), node.getType(),
                node.getHandlerVersion(), prepared.environment(), prepared.mode(), "FAILED",
                node.getSideEffect(), dataRedactor.redact(prepared.input()), null,
                usage == null ? Map.of() : Map.copyOf(usage),
                schemaSource(prepared.schema()), schemaVersion(prepared.schema()),
                schemaDiagnostics(prepared.schema()), errorCode,
                safeMessage(new IllegalStateException(errorMessage)), durationMs);
    }

    private WorkflowNodeTestResult cancelled(
            String testRunId, PreparedTest prepared, long durationMs) {
        return cancelled(testRunId, prepared, durationMs, Map.of());
    }

    private WorkflowNodeTestResult cancelled(
            String testRunId, PreparedTest prepared, long durationMs,
            Map<String, Number> usage) {
        WorkflowNodeTestResult result = failed(
                testRunId, prepared, WorkflowErrorCode.NODE_CANCELLED.name(),
                "单节点试运行已取消", durationMs, usage);
        return new WorkflowNodeTestResult(
                result.testRunId(), result.definitionId(), result.draftRevision(),
                result.nodeId(), result.nodeType(), result.handlerVersion(),
                result.environment(), result.mode(), "CANCELLED", result.sideEffect(), result.input(),
                result.output(), result.usage(), result.schemaSource(),
                result.schemaSourceVersion(), result.schemaDiagnostics(),
                result.errorCode(), result.errorMessage(), result.durationMs());
    }

    private WorkflowNodeTestRun queuedRecord(
            String testRunId, PreparedTest prepared) {
        WorkflowNodeTestRun record = new WorkflowNodeTestRun();
        WorkflowExecutionPlan.PlanNode node = prepared.node();
        record.setTenantId(prepared.definition().tenantId());
        record.setTestRunId(testRunId);
        record.setDefinitionId(prepared.definition().id());
        record.setDraftRevision(prepared.definition().draftRevision());
        record.setNodeId(node.getId());
        record.setNodeType(node.getType());
        record.setHandlerVersion(node.getHandlerVersion());
        record.setEnvironment(prepared.environment());
        record.setTestMode(prepared.mode());
        record.setStatus("QUEUED");
        record.setSideEffect(node.getSideEffect());
        record.setPrincipalType(prepared.principalType());
        record.setPrincipalId(prepared.principalId());
        record.setInputJson(writeJson(dataRedactor.redact(prepared.input())));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("payloadVersion", 1);
        payload.put("mode", prepared.mode());
        payload.set("input", prepared.input());
        record.setPayloadCiphertext(payloadCipher.encrypt(writeJson(payload)));
        record.setUsageJson("{}");
        record.setSchemaSource(schemaSource(prepared.schema()));
        record.setSchemaSourceVersion(schemaVersion(prepared.schema()));
        record.setSchemaDiagnosticsJson(writeJson(schemaDiagnostics(prepared.schema())));
        record.setNodeConfigHash(nodeConfigHash(node));
        record.setTimeoutSeconds(prepared.timeoutSeconds());
        record.setCancelRequested(false);
        record.setFencingToken(0L);
        record.setAttemptCount(0);
        Date now = new Date();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    private WorkflowNodeTestAudit auditRecord(WorkflowNodeTestRun run) {
        WorkflowNodeTestAudit audit = new WorkflowNodeTestAudit();
        audit.setTenantId(run.getTenantId());
        audit.setTestRunId(run.getTestRunId());
        audit.setDefinitionId(run.getDefinitionId());
        audit.setDraftRevision(run.getDraftRevision());
        audit.setNodeId(run.getNodeId());
        audit.setNodeType(run.getNodeType());
        audit.setHandlerVersion(run.getHandlerVersion());
        audit.setEnvironment(run.getEnvironment());
        audit.setTestMode(run.getTestMode());
        audit.setSideEffect(run.getSideEffect());
        audit.setPrincipalType(run.getPrincipalType());
        audit.setPrincipalId(run.getPrincipalId());
        audit.setNodeConfigHash(run.getNodeConfigHash());
        audit.setSchemaSource(run.getSchemaSource());
        audit.setSchemaSourceVersion(run.getSchemaSourceVersion());
        audit.setCreateTime(new Date());
        return audit;
    }

    private Long totalTokens(Map<String, Number> usage) {
        BigDecimal total = usageAmount(usage, "totalTokens", "total_tokens");
        if (total == null) {
            BigDecimal input = usageAmount(usage, "inputTokens", "input_tokens");
            BigDecimal output = usageAmount(usage, "outputTokens", "output_tokens");
            if (input != null || output != null) {
                total = (input == null ? BigDecimal.ZERO : input)
                        .add(output == null ? BigDecimal.ZERO : output);
            }
        }
        if (total == null || total.signum() < 0) return null;
        try {
            return total.longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private BigDecimal costAmount(Map<String, Number> usage) {
        BigDecimal cost = usageAmount(usage, "cost", "costAmount", "cost_amount");
        if (cost == null || cost.signum() < 0) return null;
        BigDecimal normalized = cost.setScale(8, RoundingMode.HALF_UP);
        return normalized.precision() <= 20 ? normalized : null;
    }

    private BigDecimal usageAmount(Map<String, Number> usage, String... keys) {
        for (String key : keys) {
            Number value = usage.get(key);
            if (value == null) continue;
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Number> normalizeUsage(Map<String, Number> usage) {
        if (usage == null || usage.isEmpty()) return Map.of();
        Map<String, Number> normalized = new LinkedHashMap<>();
        usage.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) return;
            try {
                BigDecimal amount = new BigDecimal(value.toString());
                if (amount.signum() >= 0) normalized.put(key, amount.stripTrailingZeros());
            } catch (NumberFormatException ignored) {
                // 审计和费用摘要只接受有限、非负的数值。
            }
        });
        return Map.copyOf(normalized);
    }

    private WorkflowNodeTestRun requireRecord(String testRunId) {
        if (testRunId == null || testRunId.isBlank()) {
            throw new ServiceException("试运行任务ID不能为空");
        }
        WorkflowNodeTestRun record = testRunMapper.selectByTestRunId(testRunId);
        if (record == null) throw new ServiceException("单节点试运行任务不存在");
        return record;
    }

    private void requireAccess(WorkflowNodeTestRun record) {
        definitions.getDefinition(record.getDefinitionId());
    }

    private WorkflowNodeTestResult toResult(WorkflowNodeTestRun record) {
        return new WorkflowNodeTestResult(
                record.getTestRunId(), record.getDefinitionId(), record.getDraftRevision(),
                record.getNodeId(), record.getNodeType(), record.getHandlerVersion(),
                record.getEnvironment(), record.getTestMode() == null ? "NODE" : record.getTestMode(),
                record.getStatus(), record.getSideEffect(),
                readTree(record.getInputJson()), readTree(record.getOutputJson()),
                readMap(record.getUsageJson()), record.getSchemaSource(),
                record.getSchemaSourceVersion(), readList(record.getSchemaDiagnosticsJson()),
                record.getErrorCode(), record.getErrorMessage(),
                record.getDurationMs() == null ? 0L : record.getDurationMs());
    }

    private int timeoutSeconds(
            WorkflowExecutionPlan.PlanNode node, WorkflowNodeTestCommand command) {
        int requested = command == null || command.timeoutSeconds() == null
                ? Math.min(node.getTimeoutSeconds() == null ? 60 : node.getTimeoutSeconds(), 60)
                : command.timeoutSeconds();
        if (requested < 1 || requested > MAX_TIMEOUT_SECONDS) {
            throw new ServiceException("试运行超时只能设置为1到120秒");
        }
        return requested;
    }

    private String normalizeMode(String value) {
        String mode = value == null || value.isBlank()
                ? "NODE" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NODE", "UPSTREAM_CHAIN").contains(mode)) {
            throw new ServiceException("试运行模式只能是NODE或UPSTREAM_CHAIN");
        }
        return mode;
    }

    private List<WorkflowExecutionPlan.PlanNode> chainNodes(
            WorkflowExecutionPlan plan, String targetNodeId) {
        Map<String, WorkflowExecutionPlan.PlanNode> nodes = new LinkedHashMap<>();
        plan.getNodes().forEach(node -> nodes.put(node.getId(), node));
        List<WorkflowExecutionPlan.PlanNode> reversed = new ArrayList<>();
        Set<String> visited = new java.util.LinkedHashSet<>();
        String current = targetNodeId;
        while (true) {
            if (!visited.add(current)) {
                throw new ServiceException("链式试运行不支持循环路径");
            }
            WorkflowExecutionPlan.PlanNode node = nodes.get(current);
            if (node == null) throw new ServiceException("链式试运行路径引用了不存在的节点");
            reversed.add(node);
            List<WorkflowExecutionPlan.PlanEdge> incoming = new ArrayList<>();
            for (WorkflowExecutionPlan.PlanEdge candidate : plan.getEdges()) {
                if (current.equals(candidate.getTarget())) incoming.add(candidate);
            }
            if (incoming.size() != 1) {
                throw new ServiceException("链式试运行只支持单入口线性路径: " + current);
            }
            WorkflowExecutionPlan.PlanEdge edge = incoming.get(0);
            if (!"NORMAL".equals(edge.getKind())) {
                throw new ServiceException("链式试运行只支持普通连线: " + current);
            }
            if ("__start__".equals(edge.getSource())) break;
            current = edge.getSource();
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private String normalizeEnvironment(String value) {
        String result = value == null || value.isBlank()
                ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DEV", "TEST", "PROD").contains(result)) {
            throw new ServiceException("资源环境只能是DEV、TEST或PROD");
        }
        return result;
    }

    private void ensureSize(JsonNode value, int limit, String message) {
        try {
            if (value != null && objectMapper.writeValueAsBytes(value).length > limit) {
                throw new ServiceException(message);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("试运行数据格式无效");
        }
    }

    private String safeMessage(Throwable error) {
        String value = error == null || error.getMessage() == null
                ? "节点试运行失败" : error.getMessage();
        JsonNode redacted = dataRedactor.redact(objectMapper.getNodeFactory().textNode(value));
        String result = redacted == null ? "节点试运行失败" : redacted.asText();
        result = result.replaceAll(
                "(?i)(api[-_ ]?key|authorization|password|token)\\s*[:=]\\s*[^,;\\s]+",
                "$1=[REDACTED]");
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }

    private String principalType(CallerContext caller) {
        String username = CallerUtils.getUsername();
        if (username != null && username.startsWith("apikey:")) return "API_KEY";
        return caller.isPlatformMode() ? "PLATFORM_USER" : "ADMIN";
    }

    private String principalId(CallerContext caller) {
        String username = CallerUtils.getUsername();
        if (username != null && username.startsWith("apikey:")) {
            return username.substring("apikey:".length());
        }
        return String.valueOf(caller.getUserId() == null ? 0L : caller.getUserId());
    }

    private String schemaSource(WorkflowResolvedNodeSchemaView schema) {
        return schema == null ? "NODE_CONTRACT" : schema.source();
    }

    private String schemaVersion(WorkflowResolvedNodeSchemaView schema) {
        return schema == null ? null : schema.sourceVersion();
    }

    private String nodeConfigHash(WorkflowExecutionPlan.PlanNode node) {
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("type", node.getType());
        snapshot.put("handlerVersion", node.getHandlerVersion());
        snapshot.set("config", node.getConfig());
        snapshot.set("inputMapping", objectMapper.valueToTree(node.getInputMapping()));
        snapshot.set("resourceRefs", objectMapper.valueToTree(node.getResourceRefs()));
        return WorkflowStructuredOutput.fingerprint(snapshot);
    }

    private List<String> schemaDiagnostics(WorkflowResolvedNodeSchemaView schema) {
        return schema == null ? List.of() : schema.diagnostics();
    }

    private long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("试运行结果序列化失败");
        }
    }

    private JsonNode readTree(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Number> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private record PreparedTest(
            WorkflowDefinitionView definition,
            WorkflowExecutionPlan.PlanNode node,
            WorkflowNodeHandler handler,
            String environment,
            int timeoutSeconds,
            JsonNode input,
            WorkflowResolvedNodeSchemaView schema,
            String principalType,
            String principalId,
            WorkflowResourceResolver.Resolution resources,
            WorkflowExecutionPlan plan,
            List<WorkflowExecutionPlan.PlanNode> chain,
            Map<String, WorkflowResolvedNodeSchemaView> schemas,
            String mode) {
    }

    private record PrincipalIdentity(String type, String id) {
    }

    private static class ActiveTest {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final long fencingToken;
        private volatile Future<?> future;

        private ActiveTest(long fencingToken) {
            this.fencingToken = fencingToken;
        }

        private void cancel() {
            cancelled.set(true);
            Future<?> current = future;
            if (current != null) current.cancel(true);
        }
    }
}
