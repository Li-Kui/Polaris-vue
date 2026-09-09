package com.polaris.ai.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.WorkflowDefinitionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowDefinitionView;
import com.polaris.ai.workflow.application.WorkflowNodeSchemaApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowResolvedNodeSchemaView;
import com.polaris.ai.workflow.definition.WorkflowDefinitionSpec;
import com.polaris.ai.workflow.domain.WorkflowResourceBinding;
import com.polaris.ai.workflow.mapper.WorkflowResourceBindingMapper;
import com.polaris.ai.workflow.registry.WorkflowNodeSchemaRegistry;
import com.polaris.ai.workflow.registry.WorkflowResourceRegistry;
import com.polaris.ai.workflow.runtime.WorkflowOutputSchemaGovernance;
import com.polaris.ai.workflow.spi.*;
import com.polaris.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/** 基于已保存草稿和已授权资源绑定批量解析节点有效 Schema。 */
@Slf4j
@Service
public class WorkflowNodeSchemaService implements WorkflowNodeSchemaApplicationFacade {

    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "PROD");

    private final WorkflowDefinitionApplicationFacade definitionFacade;
    private final WorkflowNodeDescriptorResolver descriptors;
    private final WorkflowNodeSchemaRegistry schemaRegistry;
    private final WorkflowResourceBindingMapper bindingMapper;
    private final WorkflowResourceRegistry resourceRegistry;
    private final ObjectMapper objectMapper;

    public WorkflowNodeSchemaService(
            WorkflowDefinitionApplicationFacade definitionFacade,
            WorkflowNodeDescriptorResolver descriptors,
            WorkflowNodeSchemaRegistry schemaRegistry,
            WorkflowResourceBindingMapper bindingMapper,
            WorkflowResourceRegistry resourceRegistry,
            ObjectMapper objectMapper) {
        this.definitionFacade = definitionFacade;
        this.descriptors = descriptors;
        this.schemaRegistry = schemaRegistry;
        this.bindingMapper = bindingMapper;
        this.resourceRegistry = resourceRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowResolvedNodeSchemaView> resolve(
            Long definitionId, String environment) {
        String normalizedEnvironment = normalizeEnvironment(environment);
        WorkflowDefinitionView definition = definitionFacade.getDefinition(definitionId);
        WorkflowDefinitionSpec spec = parseDefinition(definition.draftJson());
        CallerContext caller = CallerUtils.getContext();
        String principalType = principalType(caller);
        String principalId = String.valueOf(CallerUtils.getUserId());
        List<WorkflowResolvedNodeSchemaView> result = new ArrayList<>();
        for (WorkflowDefinitionSpec.Node node
                : spec.getNodes() == null ? List.<WorkflowDefinitionSpec.Node>of() : spec.getNodes()) {
            result.add(resolveNode(
                    definition, node, normalizedEnvironment, principalType, principalId));
        }
        return List.copyOf(result);
    }

    private WorkflowResolvedNodeSchemaView resolveNode(
            WorkflowDefinitionView definition,
            WorkflowDefinitionSpec.Node node,
            String environment,
            String principalType,
            String principalId) {
        List<String> diagnostics = new ArrayList<>();
        WorkflowNodeDescriptor descriptor = descriptors.find(
                        node.getType(), node.getTypeVersion())
                .orElse(null);
        JsonNode inputSchema = descriptor == null ? emptySchema() : descriptor.inputSchema();
        JsonNode outputSchema = descriptor == null ? emptySchema() : descriptor.outputSchema();
        if (descriptor == null) {
            diagnostics.add("节点处理器未注册，无法解析有效 Schema");
            return view(node, inputSchema, outputSchema,
                    "UNKNOWN", null, Map.of(), diagnostics);
        }

        Map<String, ResolvedWorkflowResource> resources = resolveResources(
                definition, node, environment, principalType, principalId, diagnostics);
        WorkflowNodeSchemaContext context = new WorkflowNodeSchemaContext(
                node.getId(), node.getType(), node.getTypeVersion(), node.getConfig(),
                definition.tenantId(), environment, principalType, principalId,
                resources, inputSchema, outputSchema);
        try {
            ResolvedNodeSchema resolved = schemaRegistry
                    .find(node.getType(), node.getTypeVersion())
                    .map(resolver -> resolver.resolve(context))
                    .orElse(null);
            if (resolved == null) {
                return view(node, inputSchema, outputSchema,
                        "NODE_CONTRACT", node.getTypeVersion(), Map.of(), diagnostics);
            }
            diagnostics.addAll(resolved.diagnostics());
            return view(
                    node,
                    resolved.inputSchema() == null ? inputSchema : resolved.inputSchema(),
                    resolved.outputSchema() == null ? outputSchema : resolved.outputSchema(),
                    resolved.source(),
                    resolved.sourceVersion(),
                    resolved.fieldSources(),
                    diagnostics);
        } catch (Exception e) {
            log.warn("解析工作流节点动态 Schema 失败: definitionId={}, nodeId={}, type={}",
                    definition.id(), node.getId(), node.getType(), e);
            diagnostics.add("动态 Schema 解析失败，已回退到节点契约");
            return view(node, inputSchema, outputSchema,
                    "NODE_CONTRACT", node.getTypeVersion(), Map.of(), diagnostics);
        }
    }

    private Map<String, ResolvedWorkflowResource> resolveResources(
            WorkflowDefinitionView definition,
            WorkflowDefinitionSpec.Node node,
            String environment,
            String principalType,
            String principalId,
            List<String> diagnostics) {
        Map<String, ResolvedWorkflowResource> result = new LinkedHashMap<>();
        String ownerType = definition.tenantId() == null ? "SYSTEM" : "TENANT";
        Long ownerId = definition.tenantId() == null ? 0L : definition.tenantId();
        for (WorkflowDefinitionSpec.ResourceRef reference : node.getResourceRefs() == null
                ? List.<WorkflowDefinitionSpec.ResourceRef>of() : node.getResourceRefs()) {
            String kind = normalizeKind(reference.getKind());
            String key = reference.getKey();
            if (kind == null || key == null || key.isBlank()) {
                diagnostics.add("节点资源引用格式无效");
                continue;
            }
            WorkflowResourceBinding binding = bindingMapper.selectActive(
                    ownerType, ownerId, "WORKFLOW", definition.id(), environment, kind, key);
            // 中台资源必须由当前工作流显式绑定；仅管理端系统工作流保留 OWNER 默认值。
            if (binding == null && definition.tenantId() == null) {
                binding = bindingMapper.selectActive(
                        ownerType, ownerId, "OWNER", ownerId, environment, kind, key);
            }
            if (binding == null) {
                if (Boolean.TRUE.equals(reference.getRequired())) {
                    diagnostics.add("必需资源尚未绑定: " + kind + ":" + key);
                }
                continue;
            }
            try {
                WorkflowResourceProvider provider = resourceRegistry.find(kind)
                        .orElseThrow(() -> new ServiceException("资源提供器未注册"));
                WorkflowResourceRequest request = new WorkflowResourceRequest(
                        definition.tenantId(), environment, kind, key,
                        binding.getResourceId(), principalType, principalId);
                List<String> errors = provider.validate(request);
                if (errors != null && !errors.isEmpty()) {
                    diagnostics.add("资源不可用于 Schema 解析: " + kind + ":" + key);
                    continue;
                }
                ResolvedWorkflowResource resource = provider.resolve(request);
                if (resource != null) result.put(key, resource);
            } catch (Exception e) {
                log.warn("解析工作流节点 Schema 资源失败: definitionId={}, nodeId={}, kind={}, key={}",
                        definition.id(), node.getId(), kind, key, e);
                diagnostics.add("资源解析失败: " + kind + ":" + key);
            }
        }
        return Map.copyOf(result);
    }

    private WorkflowResolvedNodeSchemaView view(
            WorkflowDefinitionSpec.Node node,
            JsonNode inputSchema,
            JsonNode outputSchema,
            String source,
            String sourceVersion,
            Map<String, String> fieldSources,
            List<String> diagnostics) {
        JsonNode effectiveOutputSchema = outputSchema;
        String effectiveSource = source;
        String effectiveSourceVersion = sourceVersion;
        Map<String, String> effectiveFieldSources = fieldSources;
        List<String> effectiveDiagnostics = new ArrayList<>(diagnostics);
        JsonNode override = node.getOutputSchemaOverride();
        if (override != null && !override.isNull()) {
            List<String> errors = WorkflowOutputSchemaGovernance.validate(override);
            if (errors.isEmpty()) {
                WorkflowOutputSchemaGovernance.conflicts(outputSchema, override)
                        .forEach(message -> effectiveDiagnostics.add(
                                "正式输出覆盖与基础契约存在差异: " + message));
                effectiveOutputSchema = override.deepCopy();
                effectiveSource = "USER_OVERRIDE";
                effectiveSourceVersion = WorkflowOutputSchemaGovernance.fingerprint(override);
                Map<String, String> overrideSources = new LinkedHashMap<>();
                overrideSources.put("$", "USER_OVERRIDE");
                overrideSources.put("$baseHash",
                        WorkflowOutputSchemaGovernance.fingerprint(outputSchema));
                overrideSources.put("$baseSource", source == null ? "NODE_CONTRACT" : source);
                if (sourceVersion != null) {
                    overrideSources.put("$baseSourceVersion", sourceVersion);
                }
                effectiveFieldSources = Map.copyOf(overrideSources);
            } else {
                effectiveDiagnostics.add("正式输出覆盖无效: " + errors.get(0));
            }
        }
        return new WorkflowResolvedNodeSchemaView(
                node.getId(), node.getType(), node.getTypeVersion(), inputSchema,
                effectiveOutputSchema, effectiveSource, effectiveSourceVersion,
                Map.copyOf(effectiveFieldSources), List.copyOf(effectiveDiagnostics));
    }

    private WorkflowDefinitionSpec parseDefinition(String draftJson) {
        try {
            return objectMapper.readValue(draftJson, WorkflowDefinitionSpec.class);
        } catch (Exception e) {
            throw new ServiceException("工作流草稿格式无效，无法解析节点 Schema");
        }
    }

    private JsonNode emptySchema() {
        return objectMapper.createObjectNode()
                .put("type", "object")
                .set("properties", objectMapper.createObjectNode());
    }

    private String normalizeEnvironment(String value) {
        String result = value == null || value.isBlank()
                ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(result)) {
            throw new ServiceException("资源环境只能是DEV、TEST或PROD");
        }
        return result;
    }

    private String normalizeKind(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        return result.matches("[A-Z][A-Z0-9_]{0,63}") ? result : null;
    }

    private String principalType(CallerContext caller) {
        if (CallerUtils.getUsername().startsWith("apikey:")) return "API_KEY";
        return caller.isPlatformMode() ? "PLATFORM_USER" : "ADMIN";
    }
}
