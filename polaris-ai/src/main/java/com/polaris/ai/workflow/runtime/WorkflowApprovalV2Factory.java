package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.application.WorkflowArtifactView;
import com.polaris.ai.workflow.compiler.WorkflowExpressionParser;
import com.polaris.ai.workflow.domain.WorkflowApprovalAssignment;
import com.polaris.ai.workflow.domain.WorkflowApprovalInstance;
import com.polaris.ai.workflow.domain.WorkflowApprovalStage;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.service.WorkflowArtifactService;
import com.polaris.ai.workflow.spi.WorkflowApprovalPrincipal;
import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将已发布的V2审批配置转换为不可变运行快照。 */
@Component
public class WorkflowApprovalV2Factory {

    private static final int MAX_STAGES = 20;
    private static final int MAX_TARGETS = 20;
    private static final int MAX_ASSIGNEES = 500;
    private static final int MAX_CONTENT_BYTES = 256 * 1024;
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([^{}]+)}");

    private final WorkflowApprovalDirectoryResolver directoryResolver;
    private final ObjectMapper objectMapper;
    private final WorkflowExpressionParser expressionParser = new WorkflowExpressionParser();
    private final WorkflowExpressionEvaluator expressionEvaluator;
    private final WorkflowArtifactService artifactService;

    public WorkflowApprovalV2Factory(
            WorkflowApprovalDirectoryResolver directoryResolver,
            ObjectMapper objectMapper) {
        this(directoryResolver, objectMapper, null);
    }

    @Autowired
    public WorkflowApprovalV2Factory(
            WorkflowApprovalDirectoryResolver directoryResolver,
            ObjectMapper objectMapper,
            WorkflowArtifactService artifactService) {
        this.directoryResolver = directoryResolver;
        this.objectMapper = objectMapper;
        this.artifactService = artifactService;
        this.expressionEvaluator = new WorkflowExpressionEvaluator(objectMapper);
    }

    public Creation create(
            WorkflowExecution execution,
            String nodeRunId,
            JsonNode config,
            String nodeName,
            JsonNode context) {
        requireV2(config);
        JsonNode stages = config.path("stages");
        if (!stages.isArray() || stages.isEmpty() || stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("审批级别数量必须为1到" + MAX_STAGES + "个");
        }
        Date now = new Date();
        WorkflowApprovalInstance instance = new WorkflowApprovalInstance();
        instance.setTenantId(execution.getTenantId());
        instance.setApprovalInstanceId(UUID.randomUUID().toString());
        instance.setExecutionId(execution.getExecutionId());
        instance.setNodeRunId(nodeRunId);
        instance.setConfigVersion("2.0");
        instance.setConfigSnapshot(writeJson(config));
        instance.setContentSnapshot(contentSnapshot(
                execution, nodeRunId, nodeName, config.path("content"), context, now));
        instance.setResultMode(resultMode(config));
        instance.setStatus("CREATED");
        instance.setDeadline(deadline(config.path("deadline"), now));
        instance.setEscalationCount(0);
        instance.setLockVersion(0);
        instance.setCreateTime(now);
        instance.setUpdateTime(now);

        Activation activation = activate(
                instance, config, 0, execution.getPrincipalId(), now);
        instance.setReminderTime(reminderTime(
                config.path("reminder"), earliest(instance.getDeadline(),
                        activation.stage().getDeadline())));
        instance.setCurrentStageId(activation.stage().getStageInstanceId());
        instance.setCurrentStageSequence(1);
        instance.setStatus(activation.configurationError() ? "CONFIG_ERROR" : "PENDING");
        if (activation.configurationError()) {
            instance.setFinishTime(now);
        }
        return new Creation(instance, activation);
    }

    private String contentSnapshot(
            WorkflowExecution execution,
            String nodeRunId,
            String nodeName,
            JsonNode content,
            JsonNode context,
            Date now) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("executionId", execution.getExecutionId());
        snapshot.put("workflowCode", execution.getWorkflowCode());
        snapshot.put("workflowVersionId", execution.getWorkflowVersionId());
        snapshot.put("nodeRunId", nodeRunId);
        snapshot.put("nodeName", nodeName == null ? "人工审批" : nodeName);
        snapshot.put("initiatorId", execution.getPrincipalId());
        snapshot.put("initiatorName", principalName(execution));
        snapshot.put("createdAt", now.getTime());
        snapshot.put("title", renderTemplate(
                content.path("titleTemplate").asText("请审批当前工作流任务"), context));
        snapshot.put("description", renderTemplate(
                content.path("descriptionTemplate").asText(""), context));
        ArrayNode fields = snapshot.putArray("fields");
        for (JsonNode configured : content.path("fields")) {
            ObjectNode item = fields.addObject();
            item.put("key", configured.path("key").asText());
            item.put("label", configured.path("label").asText());
            item.put("displayType", configured.path("displayType").asText("TEXT"));
            String mask = configured.path("mask").asText("NONE");
            item.put("mask", mask);
            JsonNode source = configured.path("source");
            boolean fixedValue = source.has("value");
            JsonNode value = fixedValue
                    ? source.get("value")
                    : evaluatePathRaw(source.path("expression").asText(), context);
            boolean available = fixedValue || value != null && !value.isMissingNode();
            item.put("available", available);
            if (!available) item.put("unavailableReason", "运行数据中没有找到该字段");
            if (!available) value = NullNode.instance;
            item.set("value", mask(safeSnapshotValue(value, 0), mask,
                    configured.path("maskPattern").asText("")));
        }
        String json = writeJson(snapshot);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            if (artifactService == null) {
                throw new IllegalArgumentException("审批单内容超过256KB，请减少展示字段或大对象内容");
            }
            WorkflowArtifactView artifact = artifactService.storeJson(
                    execution.getTenantId(), execution.getExecutionId(), nodeRunId,
                    snapshot, "approval-" + nodeRunId + ".json", "application/json", 365);
            ObjectNode compact = objectMapper.createObjectNode();
            compact.put("executionId", execution.getExecutionId());
            compact.put("workflowCode", execution.getWorkflowCode());
            compact.put("nodeRunId", nodeRunId);
            compact.put("title", snapshot.path("title").asText());
            compact.put("description", "审批内容较大，已保存为受权限保护的工作流产物");
            compact.put("artifactId", artifact.artifactId());
            compact.put("artifactFileName", artifact.fileName());
            compact.put("artifactSizeBytes", artifact.sizeBytes());
            compact.putArray("fields");
            return writeJson(compact);
        }
        return json;
    }

    private String principalName(WorkflowExecution execution) {
        try {
            JsonNode principal = objectMapper.readTree(execution.getPrincipalSnapshot());
            String username = principal.path("username").asText("").trim();
            return username.isEmpty() ? "工作流发起人" : username;
        } catch (Exception exception) {
            return "工作流发起人";
        }
    }

    private String renderTemplate(String template, JsonNode context) {
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            JsonNode value = evaluatePath(matcher.group(1).trim(), context);
            String replacement = value == null || value.isNull() || value.isMissingNode()
                    ? "" : value.isValueNode() ? value.asText() : value.toString();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private JsonNode evaluatePath(String expression, JsonNode context) {
        JsonNode value = evaluatePathRaw(expression, context);
        return value == null || value.isMissingNode() ? NullNode.instance : value;
    }

    private JsonNode evaluatePathRaw(String expression, JsonNode context) {
        if (expression == null || expression.isBlank()) return NullNode.instance;
        JsonNode ast = expressionParser.parse(expression);
        if (!"path".equals(ast.path("type").asText())) {
            throw new IllegalArgumentException("审批单字段只允许读取变量路径");
        }
        JsonNode value = expressionEvaluator.evaluate(ast, context);
        return value == null ? NullNode.instance : value.deepCopy();
    }

    private JsonNode safeSnapshotValue(JsonNode value, int depth) {
        if (value == null || value.isNull()) return NullNode.instance;
        if (depth >= 10 && value.isContainerNode()) {
            return objectMapper.getNodeFactory().textNode("[内容层级超过10层，已省略]");
        }
        if (value.isTextual()) {
            String text = value.asText();
            return objectMapper.getNodeFactory().textNode(
                    text.length() > 5000 ? text.substring(0, 5000) + "…" : text);
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(safeSnapshotValue(item, depth + 1)));
            return result;
        }
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            value.fields().forEachRemaining(entry ->
                    result.set(entry.getKey(), safeSnapshotValue(entry.getValue(), depth + 1)));
            return result;
        }
        return value.deepCopy();
    }

    private JsonNode mask(JsonNode value, String policy, String pattern) {
        if (value == null || value.isNull()) return NullNode.instance;
        String text = value.isValueNode() ? value.asText() : value.toString();
        return switch (policy) {
            case "HIDDEN" -> objectMapper.getNodeFactory().textNode("******");
            case "PARTIAL" -> objectMapper.getNodeFactory().textNode(
                    partial(text));
            case "PHONE" -> objectMapper.getNodeFactory().textNode(maskPhone(text));
            case "EMAIL" -> objectMapper.getNodeFactory().textNode(maskEmail(text));
            case "ID_CARD" -> objectMapper.getNodeFactory().textNode(maskIdCard(text));
            case "CUSTOM" -> objectMapper.getNodeFactory().textNode(maskCustom(text, pattern));
            default -> value.deepCopy();
        };
    }

    private static String maskPhone(String value) {
        return value != null && value.matches(".*\\d{7,}.*") && value.length() >= 7
                ? value.substring(0, 3) + "****" + value.substring(value.length() - 4)
                : partial(value);
    }

    private static String maskEmail(String value) {
        int at = value == null ? -1 : value.indexOf('@');
        if (at <= 0) return partial(value);
        return partial(value.substring(0, at)) + value.substring(at);
    }

    private static String maskIdCard(String value) {
        if (value == null || value.length() < 8) return partial(value);
        return value.substring(0, 3) + "*".repeat(value.length() - 7)
                + value.substring(value.length() - 4);
    }

    private static String maskCustom(String value, String pattern) {
        if (pattern == null || pattern.isBlank()) return partial(value);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char marker = index < pattern.length() ? pattern.charAt(index) : '*';
            result.append(marker == '#' ? value.charAt(index) : '*');
        }
        return result.toString();
    }

    private static String partial(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() <= 2) return "*".repeat(value.length());
        if (value.length() <= 6) {
            return value.charAt(0) + "*".repeat(value.length() - 2)
                    + value.charAt(value.length() - 1);
        }
        return value.substring(0, 2) + "*".repeat(value.length() - 4)
                + value.substring(value.length() - 2);
    }

    public Activation activateNext(
            WorkflowApprovalInstance instance,
            int zeroBasedStageIndex,
            String initiatorId) {
        try {
            JsonNode config = objectMapper.readTree(instance.getConfigSnapshot());
            return activate(instance, config, zeroBasedStageIndex, initiatorId, new Date());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("审批配置快照已损坏", ex);
        }
    }

    public Activation activateReplacement(
            WorkflowApprovalInstance instance,
            int zeroBasedStageIndex,
            String initiatorId,
            List<WorkflowApprovalTarget> replacementTargets) {
        try {
            JsonNode parsed = objectMapper.readTree(instance.getConfigSnapshot());
            ObjectNode config = (ObjectNode) parsed.deepCopy();
            ObjectNode stage = (ObjectNode) config.path("stages").path(zeroBasedStageIndex);
            stage.set("targets", objectMapper.valueToTree(replacementTargets));
            stage.set("fallbackTargets", objectMapper.createArrayNode());
            return activate(instance, config, zeroBasedStageIndex, initiatorId, new Date());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("审批配置快照已损坏", ex);
        }
    }

    public int stageCount(WorkflowApprovalInstance instance) {
        try {
            return objectMapper.readTree(instance.getConfigSnapshot()).path("stages").size();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("审批配置快照已损坏", ex);
        }
    }

    private Activation activate(
            WorkflowApprovalInstance instance,
            JsonNode config,
            int zeroBasedStageIndex,
            String initiatorId,
            Date now) {
        JsonNode stageConfig = config.path("stages").path(zeroBasedStageIndex);
        if (!stageConfig.isObject()) {
            throw new IllegalArgumentException("审批级别配置不存在");
        }
        List<WorkflowApprovalTarget> targets = targets(stageConfig.path("targets"));
        List<WorkflowApprovalPrincipal> principals;
        try {
            principals = new ArrayList<>(
                    directoryResolver.resolve(instance.getTenantId(), targets));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            principals = new ArrayList<>();
        }
        if (principals.isEmpty() && stageConfig.path("fallbackTargets").isArray()
                && !stageConfig.path("fallbackTargets").isEmpty()) {
            try {
                principals = new ArrayList<>(directoryResolver.resolve(
                        instance.getTenantId(), targets(stageConfig.path("fallbackTargets"))));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                principals = new ArrayList<>();
            }
        }
        boolean allowSelfApproval = config.path("options")
                .path("allowSelfApproval").asBoolean(false);
        if (!allowSelfApproval && initiatorId != null) {
            principals.removeIf(principal -> initiatorId.equals(principal.userId()));
        }
        if (principals.size() > MAX_ASSIGNEES) {
            throw new IllegalArgumentException(
                    "单级审批人数超过上限" + MAX_ASSIGNEES + "人，请缩小人员范围");
        }

        JsonNode policy = stageConfig.path("decisionPolicy");
        String mode = policy.path("mode").asText("ANY").toUpperCase(Locale.ROOT);
        if (!List.of("ANY", "ALL", "N_OF_M").contains(mode)) {
            throw new IllegalArgumentException("不支持的多人审批模式：" + mode);
        }
        int required = switch (mode) {
            case "ALL" -> principals.size();
            case "N_OF_M" -> policy.path("requiredApprovals").asInt(0);
            default -> 1;
        };
        boolean thresholdInvalid = required < 1 || required > principals.size();

        WorkflowApprovalStage stage = new WorkflowApprovalStage();
        stage.setTenantId(instance.getTenantId());
        stage.setStageInstanceId(UUID.randomUUID().toString());
        stage.setApprovalInstanceId(instance.getApprovalInstanceId());
        stage.setStageKey(stageConfig.path("id").asText("stage_" + (zeroBasedStageIndex + 1)));
        stage.setSequenceNo(zeroBasedStageIndex + 1);
        stage.setStageName(stageConfig.path("name").asText("第" + (zeroBasedStageIndex + 1) + "级审批"));
        stage.setPolicySnapshot(writeJson(policy));
        stage.setRequiredApprovals(required);
        stage.setApprovedCount(0);
        stage.setRejectedCount(0);
        stage.setPendingCount(principals.size());
        Date stageDeadline = deadline(stageConfig.path("deadline"), now);
        stage.setDeadline(earliest(stageDeadline, instance.getDeadline()));
        stage.setLockVersion(0);
        stage.setCreateTime(now);
        stage.setUpdateTime(now);
        boolean configurationError = principals.isEmpty() || thresholdInvalid;
        stage.setStatus(configurationError ? "CONFIG_ERROR" : "ACTIVE");
        if (configurationError) {
            stage.setFinishTime(now);
        }

        List<WorkflowApprovalAssignment> assignments = principals.stream()
                .map(principal -> assignment(instance, stage, principal, now))
                .toList();
        return new Activation(stage, assignments, configurationError);
    }

    private WorkflowApprovalAssignment assignment(
            WorkflowApprovalInstance instance,
            WorkflowApprovalStage stage,
            WorkflowApprovalPrincipal principal,
            Date now) {
        WorkflowApprovalAssignment assignment = new WorkflowApprovalAssignment();
        assignment.setTenantId(instance.getTenantId());
        assignment.setAssignmentId(UUID.randomUUID().toString());
        assignment.setApprovalInstanceId(instance.getApprovalInstanceId());
        assignment.setStageInstanceId(stage.getStageInstanceId());
        assignment.setUserId(principal.userId());
        assignment.setUsername(principal.username());
        assignment.setDisplayName(principal.displayName());
        assignment.setDepartmentId(principal.departmentId());
        assignment.setDepartmentName(principal.departmentName());
        assignment.setSourceSnapshot(writeJson(principal.sources()));
        assignment.setStatus("PENDING");
        assignment.setCreateTime(now);
        assignment.setUpdateTime(now);
        return assignment;
    }

    private List<WorkflowApprovalTarget> targets(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > MAX_TARGETS) {
            throw new IllegalArgumentException(
                    "每个审批级别必须配置1到" + MAX_TARGETS + "组审批对象");
        }
        List<WorkflowApprovalTarget> result = new ArrayList<>();
        for (JsonNode item : node) {
            String type = item.path("type").asText("").toUpperCase(Locale.ROOT);
            List<String> ids = new ArrayList<>();
            item.path("ids").forEach(id -> {
                String value = id.asText("").trim();
                if (!value.isEmpty()) ids.add(value);
            });
            if (type.isBlank() || ids.isEmpty()) {
                throw new IllegalArgumentException("审批对象类型和ID不能为空");
            }
            result.add(new WorkflowApprovalTarget(
                    type, List.copyOf(ids), item.path("includeChildren").asBoolean(false)));
        }
        return result;
    }

    private Date deadline(JsonNode node, Date from) {
        if (!node.isObject() || node.isEmpty() || node.isNull()) return null;
        long duration = node.path("duration").asLong(0);
        String unit = node.path("unit").asText("HOUR").toUpperCase(Locale.ROOT);
        ChronoUnit chronoUnit = switch (unit) {
            case "MINUTE" -> ChronoUnit.MINUTES;
            case "HOUR" -> ChronoUnit.HOURS;
            case "DAY" -> ChronoUnit.DAYS;
            default -> throw new IllegalArgumentException("不支持的审批期限单位：" + unit);
        };
        Duration value = Duration.of(duration, chronoUnit);
        if (value.compareTo(Duration.ofMinutes(1)) < 0
                || value.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("审批期限必须在1分钟到365天之间");
        }
        if ("BUSINESS_DAY".equals(node.path("calendar").asText()) && "DAY".equals(unit)) {
            ZoneId zone = zone(node.path("timezone").asText("TENANT"));
            ZonedDateTime time = from.toInstant().atZone(zone);
            long remaining = duration;
            while (remaining > 0) {
                time = time.plusDays(1);
                if (time.getDayOfWeek() != DayOfWeek.SATURDAY
                        && time.getDayOfWeek() != DayOfWeek.SUNDAY) remaining--;
            }
            return Date.from(time.toInstant());
        }
        return Date.from(Instant.ofEpochMilli(from.getTime()).plus(value));
    }

    private Date reminderTime(JsonNode reminder, Date deadline) {
        if (deadline == null || !reminder.path("enabled").asBoolean(false)) return null;
        long duration = reminder.path("beforeDuration").asLong(0);
        if (duration < 1) return null;
        ChronoUnit unit = switch (reminder.path("beforeUnit").asText("HOUR")) {
            case "MINUTE" -> ChronoUnit.MINUTES;
            case "DAY" -> ChronoUnit.DAYS;
            default -> ChronoUnit.HOURS;
        };
        Instant time = deadline.toInstant().minus(Duration.of(duration, unit));
        return Date.from(time.isBefore(Instant.now()) ? Instant.now() : time);
    }

    private static ZoneId zone(String configured) {
        if (configured == null || configured.isBlank() || "TENANT".equals(configured)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured);
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("审批期限时区无效：" + configured);
        }
    }

    private static Date earliest(Date first, Date second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.before(second) ? first : second;
    }

    private static String resultMode(JsonNode config) {
        String mode = config.path("resultPolicy").path("mode")
                .asText("SIMPLE").toUpperCase(Locale.ROOT);
        if (!List.of("SIMPLE", "BRANCH").contains(mode)) {
            throw new IllegalArgumentException("不支持的审批结果模式：" + mode);
        }
        return mode;
    }

    private static void requireV2(JsonNode config) {
        if (!"2.0".equals(config.path("configVersion").asText())) {
            throw new IllegalArgumentException("不是V2审批配置");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("审批快照序列化失败", ex);
        }
    }

    public record Creation(
            WorkflowApprovalInstance instance,
            Activation activation) {
    }

    public record Activation(
            WorkflowApprovalStage stage,
            List<WorkflowApprovalAssignment> assignments,
            boolean configurationError) {
    }
}
