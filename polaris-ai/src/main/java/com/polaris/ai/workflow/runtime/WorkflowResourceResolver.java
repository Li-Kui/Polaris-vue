package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.domain.WorkflowResourceBinding;
import com.polaris.ai.workflow.mapper.WorkflowResourceBindingMapper;
import com.polaris.ai.workflow.registry.WorkflowResourceRegistry;
import com.polaris.ai.workflow.spi.ResolvedWorkflowResource;
import com.polaris.ai.workflow.spi.WorkflowResourceProvider;
import com.polaris.ai.workflow.spi.WorkflowResourceRequest;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 解析逻辑资源引用，且不持久化提供器句柄或凭据。 */
@Component
public class WorkflowResourceResolver {

    private final WorkflowResourceBindingMapper bindingMapper;
    private final WorkflowResourceRegistry resourceRegistry;
    private final ObjectMapper objectMapper;

    public WorkflowResourceResolver(
            WorkflowResourceBindingMapper bindingMapper,
            WorkflowResourceRegistry resourceRegistry,
            ObjectMapper objectMapper) {
        this.bindingMapper = bindingMapper;
        this.resourceRegistry = resourceRegistry;
        this.objectMapper = objectMapper;
    }

    public Resolution resolve(
            WorkflowExecutionPlan plan,
            Long definitionId,
            Long tenantId,
            String environment,
            String principalType,
            String principalId) {
        Map<String, ResolvedWorkflowResource> resources = new LinkedHashMap<>();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (WorkflowExecutionPlan.PlanNode node : plan.getNodes()) {
            for (var reference : node.getResourceRefs()) {
                String compositeKey = key(reference.getKind(), reference.getKey());
                if (resources.containsKey(compositeKey)) {
                    continue;
                }
                String ownerType = tenantId == null ? "SYSTEM" : "TENANT";
                Long ownerId = tenantId == null ? 0L : tenantId;
                WorkflowResourceBinding binding = definitionId == null ? null
                        : bindingMapper.selectActive(
                                ownerType, ownerId, "WORKFLOW", definitionId, environment,
                                reference.getKind(), reference.getKey());
                if (binding == null) {
                    binding = bindingMapper.selectActive(
                            ownerType, ownerId, "OWNER", ownerId, environment,
                            reference.getKind(), reference.getKey());
                }
                if (binding == null) {
                    if (Boolean.TRUE.equals(reference.getRequired())) {
                        throw new ServiceException("工作流资源未绑定或已停用: " + compositeKey);
                    }
                    continue;
                }
                WorkflowResourceProvider provider = resourceRegistry.find(reference.getKind())
                        .orElseThrow(() -> new ServiceException(
                                "工作流资源提供器未注册: " + reference.getKind()));
                WorkflowResourceRequest request = new WorkflowResourceRequest(
                        tenantId,
                        environment,
                        reference.getKind(),
                        reference.getKey(),
                        binding.getResourceId(),
                        principalType,
                        principalId);
                List<String> errors = provider.validate(request);
                if (errors != null && !errors.isEmpty()) {
                    throw new ServiceException("工作流资源校验失败: " + String.join("; ", errors));
                }
                ResolvedWorkflowResource resolved = provider.resolve(request);
                if (resolved == null) {
                    throw new ServiceException("工作流资源解析结果为空: " + compositeKey);
                }
                resources.put(compositeKey, resolved);
                Map<String, Object> snapshotItem = new LinkedHashMap<>();
                snapshotItem.put("kind", binding.getResourceKind());
                snapshotItem.put("key", binding.getResourceKey());
                snapshotItem.put("resourceId", binding.getResourceId());
                snapshotItem.put("bindingVersion", binding.getBindingVersion());
                // 数据源等版本化资源由提供器返回实际资源版本，重试时固定解析该版本。
                if (resolved.resourceVersion() > 0) {
                    snapshotItem.put("resourceVersion", resolved.resourceVersion());
                }
                snapshot.put(compositeKey, snapshotItem);
            }
        }
        return new Resolution(writeJson(snapshot), Map.copyOf(resources));
    }

    public Map<String, ResolvedWorkflowResource> forNode(
            WorkflowExecutionPlan.PlanNode node,
            Map<String, ResolvedWorkflowResource> allResources) {
        Map<String, ResolvedWorkflowResource> result = new LinkedHashMap<>();
        for (var reference : node.getResourceRefs()) {
            ResolvedWorkflowResource resource = allResources.get(
                    key(reference.getKind(), reference.getKey()));
            if (resource != null) {
                result.put(reference.getKey(), resource);
            }
        }
        return result;
    }

    public Resolution resolveSnapshot(
            WorkflowExecutionPlan plan,
            Long tenantId,
            String environment,
            String principalType,
            String principalId,
            String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return new Resolution("{}", Map.of());
        }
        try {
            JsonNode snapshot = objectMapper.readTree(snapshotJson);
            Map<String, ResolvedWorkflowResource> resources = new LinkedHashMap<>();
            for (WorkflowExecutionPlan.PlanNode node : plan.getNodes()) {
                for (var reference : node.getResourceRefs()) {
                    String compositeKey = key(reference.getKind(), reference.getKey());
                    if (resources.containsKey(compositeKey)) continue;
                    JsonNode item = snapshot.path(compositeKey);
                    if (item.isMissingNode()) {
                        if (Boolean.TRUE.equals(reference.getRequired())) {
                            throw new ServiceException("执行资源快照缺少必需绑定: " + compositeKey);
                        }
                        continue;
                    }
                    WorkflowResourceProvider provider = resourceRegistry.find(reference.getKind())
                            .orElseThrow(() -> new ServiceException(
                                    "工作流资源提供器未注册: " + reference.getKind()));
                    WorkflowResourceRequest request = new WorkflowResourceRequest(
                            tenantId,
                            environment,
                            reference.getKind(),
                            reference.getKey(),
                            item.path("resourceId").asText(),
                            principalType,
                            principalId,
                            item.has("resourceVersion")
                                    ? item.path("resourceVersion").asInt() : null);
                    List<String> errors = provider.validate(request);
                    if (errors != null && !errors.isEmpty()) {
                        throw new ServiceException("执行资源快照校验失败: "
                                + String.join("; ", errors));
                    }
                    ResolvedWorkflowResource resolved = provider.resolve(request);
                    if (resolved == null) {
                        throw new ServiceException("执行资源快照解析结果为空: " + compositeKey);
                    }
                    resources.put(compositeKey, resolved);
                }
            }
            return new Resolution(snapshotJson, Map.copyOf(resources));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("执行资源绑定快照格式无效");
        }
    }

    private String key(String kind, String key) {
        return kind + ":" + key;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("资源绑定快照序列化失败");
        }
    }

    public record Resolution(
            String snapshotJson,
            Map<String, ResolvedWorkflowResource> resources) {
    }
}
