package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.domain.WorkflowDefinition;
import com.polaris.ai.workflow.domain.WorkflowVersion;
import com.polaris.ai.workflow.mapper.WorkflowDefinitionMapper;
import com.polaris.ai.workflow.mapper.WorkflowVersionMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.*;

/** 子流程选择和编译共享同一份不可变契约，不读取子流程草稿。 */
@Service
public class WorkflowSubWorkflowService {
    private final WorkflowDefinitionMapper definitions;
    private final WorkflowVersionMapper versions;
    private final ObjectMapper mapper;

    public WorkflowSubWorkflowService(WorkflowDefinitionMapper definitions,
            WorkflowVersionMapper versions, ObjectMapper mapper) {
        this.definitions = definitions;
        this.versions = versions;
        this.mapper = mapper;
    }

    public record Contract(Long definitionId, String workflowCode, String name,
            String description, String versionId, Integer versionNo, String contentHash,
            JsonNode inputSchema, JsonNode outputSchema, boolean writes, String unavailableReason) {}

    public List<Contract> catalog(Long parentId) {
        Long tenant = currentTenant();
        var query = new LambdaQueryWrapper<WorkflowDefinition>().eq(WorkflowDefinition::getDelFlag, "0");
        if (tenant == null) query.isNull(WorkflowDefinition::getTenantId);
        else query.eq(WorkflowDefinition::getTenantId, tenant);
        return definitions.selectList(query.orderByDesc(WorkflowDefinition::getUpdateTime)).stream().map(d -> {
            try {
                if (Objects.equals(parentId, d.getId())) throw new ServiceException("不能调用当前工作流");
                var contract = resolve(d.getId(), null, tenant);
                if (parentId != null) assertDependencies(contract.versionId(), tenant,
                        new LinkedHashSet<>(Set.of(parentId)), 1, new int[]{0});
                return contract;
            } catch (RuntimeException e) {
                return new Contract(d.getId(), d.getWorkflowCode(), d.getWorkflowName(), d.getDescription(),
                        null, null, null, mapper.createObjectNode(), mapper.createObjectNode(), false, e.getMessage());
            }
        }).toList();
    }

    public static Long currentTenant() {
        if (!CallerUtils.isPlatformMode()) return null;
        try {
            long id = Long.parseLong(CallerUtils.getTenantId());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (RuntimeException e) { throw new ServiceException("中台租户ID格式错误"); }
    }

    public Contract resolve(Long id, String versionId, Long tenant) {
        WorkflowDefinition definition = definitions.selectById(id);
        if (definition == null || !Objects.equals(tenant, definition.getTenantId())
                || !"0".equals(definition.getDelFlag())) throw new ServiceException("子工作流不存在或无权访问");
        if (!"ACTIVE".equals(definition.getStatus())) throw new ServiceException("子工作流已停用");
        String selected = versionId == null || versionId.isBlank() ? definition.getCurrentPublishedVersionId() : versionId;
        if (selected == null) throw new ServiceException("请先发布子工作流");
        WorkflowVersion version = versions.selectByVersionId(selected);
        if (version == null || !id.equals(version.getDefinitionId()) || !"PUBLISHED".equals(version.getStatus()))
            throw new ServiceException("子工作流发布版本无效");
        WorkflowExecutionPlan plan = plan(version);
        return new Contract(id, definition.getWorkflowCode(), definition.getWorkflowName(), definition.getDescription(),
                selected, version.getVersionNo(), version.getContentHash(), plan.getInputs() == null
                    ? mapper.createObjectNode().put("type", "object") : plan.getInputs(), outputSchema(plan),
                plan.getNodes().stream().anyMatch(n -> "WRITE".equals(n.getSideEffect())
                        || n.getConfig() != null && n.getConfig().path("childWrites").asBoolean()), null);
    }

    public void assertDependencies(String versionId, Long tenant, Set<Long> ancestors, int depth, int[] visited) {
        if (depth > 5) throw new ServiceException("子工作流嵌套不能超过 5 层");
        if (++visited[0] > 1000) throw new ServiceException("子工作流依赖数量超过限制");
        WorkflowVersion version = versions.selectByVersionId(versionId);
        if (version == null) throw new ServiceException("子工作流依赖版本不存在");
        resolve(version.getDefinitionId(), versionId, tenant);
        if (!ancestors.add(version.getDefinitionId())) throw new ServiceException("子工作流存在循环调用");
        try {
            for (var node : plan(version).getNodes()) if ("sub_workflow".equals(node.getType())) {
                assertDependencies(node.getConfig().path("workflowVersionId").asText(), tenant, ancestors, depth + 1, visited);
            }
        } finally { ancestors.remove(version.getDefinitionId()); }
    }

    public WorkflowExecutionPlan plan(WorkflowVersion version) {
        try {
            var plan = mapper.readValue(version.getExecutionPlanJson(), WorkflowExecutionPlan.class);
            if (!Objects.equals(version.getContentHash(), plan.getContentHash()))
                throw new ServiceException("子工作流版本校验失败");
            return plan;
        } catch (java.io.IOException e) { throw new ServiceException("子工作流执行计划无效"); }
    }

    public Set<Long> parentIds(String code, Long tenant) {
        var query = new LambdaQueryWrapper<WorkflowDefinition>().eq(WorkflowDefinition::getWorkflowCode, code)
                .eq(WorkflowDefinition::getDelFlag, "0");
        if (tenant == null) query.isNull(WorkflowDefinition::getTenantId); else query.eq(WorkflowDefinition::getTenantId, tenant);
        return new LinkedHashSet<>(definitions.selectList(query).stream().map(WorkflowDefinition::getId).toList());
    }

    public ObjectNode envelope(JsonNode result) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        var props = schema.putObject("properties");
        // 未完成出口没有业务结果；成功时由子流程本身和父节点共同校验。
        ObjectNode resultSchema = result != null && result.isObject() ? result.deepCopy() : mapper.createObjectNode();
        if (resultSchema.path("type").isTextual()) {
            String type = resultSchema.path("type").asText();
            resultSchema.putArray("type").add(type).add("null");
        }
        props.set("result", resultSchema);
        var execution = props.putObject("execution").put("type", "object");
        var meta = execution.putObject("properties");
        meta.putObject("executionId").put("type", "string").put("title", "子流程运行记录");
        meta.putObject("status").put("type", "string").put("title", "运行状态");
        meta.putObject("errorCode").put("type", "string").put("title", "错误原因");
        schema.putArray("required").add("result").add("execution");
        return schema;
    }

    public ObjectNode outputSchema(WorkflowExecutionPlan plan) {
        var schema = mapper.createObjectNode().put("type", "object");
        var properties = schema.putObject("properties");
        if (plan.getOutputs().isEmpty()) {
            // 引擎未声明输出映射时返回 nodes.<id>.output；不能伪装成空对象。
            for (var node : plan.getNodes()) {
                var wrapper = properties.putObject(node.getId()).put("type", "object");
                wrapper.putObject("properties").set("output", node.getOutputSchema() == null
                        ? mapper.createObjectNode() : node.getOutputSchema().deepCopy());
            }
            return schema;
        }
        plan.getOutputs().forEach((key, binding) -> properties.set(key, binding.getExpressionAst() == null
                ? literalSchema(binding.getValue()) : pathSchema(plan, binding.getExpressionAst())));
        return schema;
    }

    private JsonNode pathSchema(WorkflowExecutionPlan plan, JsonNode ast) {
        if (!"path".equals(ast.path("type").asText())) return mapper.createObjectNode();
        String path = ast.path("value").asText();
        JsonNode root;
        if (path.equals("$.input") || path.startsWith("$.input.")) {
            root = plan.getInputs(); path = path.substring(7);
        } else if (path.startsWith("$.nodes.")) {
            String rest = path.substring(8);
            int split = rest.indexOf('.');
            String id = split < 0 ? rest : rest.substring(0, split);
            var node = plan.getNodes().stream().filter(n -> id.equals(n.getId())).findFirst().orElse(null);
            if (node == null || split < 0 || !(rest.substring(split).equals(".output")
                    || rest.substring(split).startsWith(".output.") || rest.substring(split).startsWith(".output["))) return mapper.createObjectNode();
            root = node.getOutputSchema(); path = rest.substring(split + 7);
        } else return mapper.createObjectNode();
        for (String segment : path.replaceAll("\\[(\\d+)\\]", ".$1").split("\\.")) {
            if (segment.isEmpty()) continue;
            if (root == null) return mapper.createObjectNode();
            root = segment.matches("\\d+") ? root.path("items") : root.path("properties").path(segment);
        }
        return root == null || root.isMissingNode() ? mapper.createObjectNode() : root.deepCopy();
    }

    private JsonNode literalSchema(JsonNode value) {
        return literalSchemas(List.of(value == null ? mapper.nullNode() : value), 0);
    }

    /** 常量数组按所有元素合并类型与嵌套字段，不使用校验器不支持的 anyOf。 */
    private JsonNode literalSchemas(List<JsonNode> values, int depth) {
        var schema = mapper.createObjectNode();
        if (values.isEmpty() || depth > 20) return schema;
        Set<String> types = new LinkedHashSet<>();
        Map<String, List<JsonNode>> fields = new LinkedHashMap<>();
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode value : values) {
            types.add(value.isNull() ? "null" : value.isObject() ? "object" : value.isArray() ? "array"
                    : value.isBoolean() ? "boolean" : value.isIntegralNumber() ? "integer" : value.isNumber() ? "number" : "string");
            if (value.isObject()) value.fields().forEachRemaining(e -> fields.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
            if (value.isArray()) value.forEach(items::add);
        }
        if (types.contains("number")) types.remove("integer");
        if (types.size() == 1) schema.put("type", types.iterator().next());
        else types.forEach(schema.putArray("type")::add);
        if (types.contains("object")) {
            var properties = schema.putObject("properties");
            fields.forEach((name, fieldValues) -> properties.set(name, literalSchemas(fieldValues, depth + 1)));
        }
        if (types.contains("array")) schema.set("items", literalSchemas(items, depth + 1));
        return schema;
    }
}
