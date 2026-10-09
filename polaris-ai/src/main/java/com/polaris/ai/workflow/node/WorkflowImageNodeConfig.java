package com.polaris.ai.workflow.node;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.ai.modelcenter.runtime.ImageRuntimeService;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.RuntimePolicySpec;
import com.polaris.ai.runtime.image.ImageCapabilityInvocation;
import com.polaris.ai.workflow.application.WorkflowResourceOption;
import com.polaris.ai.workflow.security.WorkflowPrincipalSecurityContextResolver;
import com.polaris.ai.workflow.spi.*;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.*;

/** 显式绑定、单图、无自动重试的租户图片节点；不加入 Agent 工具集。 */
@Configuration
public class WorkflowImageNodeConfig {

    private static final Set<String> CAPABILITIES = Set.of("IMAGE_GENERATION", "IMAGE_EDIT");

    /** 不含运行时凭据的资源句柄。 */
    record ImageModelHandle(Long tenantId, Long modelId, int revision, Set<String> capabilities) { }

    @Bean
    public WorkflowResourceProvider workflowImageModelResourceProvider(
            AiModelConfigMapper mapper, ModelDefinitionResolver definitions) {
        return new WorkflowResourceProvider() {
            @Override public String kind() { return "IMAGE_MODEL"; }

            private AiModelConfig model(WorkflowResourceRequest request) {
                if (request.tenantId() == null) return null;
                try {
                    AiModelConfig model = mapper.selectWorkflowResource(request.tenantId(), Long.parseLong(request.resourceId()));
                    return model != null && Objects.equals(request.tenantId(), model.getTenantId()) ? model : null;
                } catch (NumberFormatException e) { return null; }
            }

            private Set<String> enabled(AiModelConfig model) {
                Set<String> available = new LinkedHashSet<>();
                for (String capability : CAPABILITIES) {
                    try { if (definitions.resolve(model.getId(), capability) != null) available.add(capability); }
                    catch (RuntimeException ignored) { /* 未启用的能力不进入安全目录。 */ }
                }
                return Set.copyOf(available);
            }

            @Override public List<String> validate(WorkflowResourceRequest request) {
                AiModelConfig model = model(request);
                if (model == null) return List.of("图片模型不存在或不属于当前租户");
                if (!"1".equals(model.getStatus())) return List.of("图片模型已停用");
                if (request.resourceVersion() != null && request.resourceVersion() != revision(model)) {
                    return List.of("图片模型版本已变化，请重新发布工作流");
                }
                return enabled(model).isEmpty() ? List.of("模型未启用可用的图片生成或编辑能力") : List.of();
            }

            @Override public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                List<String> errors = validate(request);
                if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("；", errors));
                AiModelConfig model = model(request);
                if (model == null || !"1".equals(model.getStatus())
                        || (request.resourceVersion() != null && request.resourceVersion() != revision(model))) {
                    throw new IllegalArgumentException("图片模型已变化，请重新校验并发布工作流");
                }
                Set<String> capabilities = enabled(model);
                if (capabilities.isEmpty()) throw new IllegalArgumentException("模型未启用可用的图片生成或编辑能力");
                return new ResolvedWorkflowResource(kind(), request.resourceKey(), request.resourceId(), revision(model),
                        Map.of("capabilities", capabilities, "maxImages", 1, "automaticRetry", false),
                        new ImageModelHandle(request.tenantId(), model.getId(), revision(model), capabilities));
            }

            @Override public List<WorkflowResourceOption> listAvailable(WorkflowResourceCatalogRequest request) {
                if (request.tenantId() == null) return List.of();
                Map<Long, AiModelConfig> models = new LinkedHashMap<>();
                for (String capability : CAPABILITIES) {
                    for (AiModelConfig model : mapper.selectWorkflowResourcesByCapability(request.tenantId(), capability)) {
                        if (Objects.equals(request.tenantId(), model.getTenantId())) models.put(model.getId(), model);
                    }
                }
                return models.values().stream().map(model -> {
                    var resource = new WorkflowResourceRequest(request.tenantId(), request.environment(), kind(),
                            "catalog", String.valueOf(model.getId()), request.principalType(), request.principalId());
                    List<String> errors = validate(resource);
                    return new WorkflowResourceOption(kind(), String.valueOf(model.getId()), model.getName(),
                            model.getModelName(), model.getStatus(), errors.isEmpty(),
                            errors.isEmpty() ? null : String.join("；", errors), false,
                            Map.of("capabilities", enabled(model), "maxImages", 1));
                }).toList();
            }
        };
    }

    @Bean
    public WorkflowNodeHandler imageWorkflowNodeHandler(ImageRuntimeService runtime,
            WorkflowPrincipalSecurityContextResolver principals, ObjectProvider<WorkflowChatMediaResolver> media) {
        return new WorkflowNodeHandler() {
            @Override public WorkflowNodeDescriptor descriptor() { return imageDescriptor(); }

            @Override public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                if (context.attemptNo() != 1) throw new IllegalArgumentException("图片节点不能自动重复调用，请先确认原执行结果");
                List<ResolvedWorkflowResource> models = context.resources().values().stream()
                        .filter(resource -> "IMAGE_MODEL".equals(resource.kind())).toList();
                if (models.size() != 1 || !(models.get(0).handle() instanceof ImageModelHandle model)
                        || context.tenantId() == null || !Objects.equals(context.tenantId(), model.tenantId())) {
                    throw new IllegalArgumentException("图片节点必须绑定本租户的一个图片模型");
                }
                String mode = context.config().path("mode").asString("GENERATE");
                if (!Set.of("GENERATE", "EDIT").contains(mode)) throw new IllegalArgumentException("图片模式无效");
                String capability = "EDIT".equals(mode) ? "IMAGE_EDIT" : "IMAGE_GENERATION";
                if (!model.capabilities().contains(capability)) throw new IllegalArgumentException("绑定模型未启用所选图片能力");
                JsonNode input = context.input();
                String prompt = input == null ? "" : input.path("prompt").asString();
                if (prompt.isBlank() || prompt.length() > 8000) throw new IllegalArgumentException("图片描述不能为空且不能超过8000字符");
                String size = context.config().path("size").asString("1024x1024");
                if (!Set.of("1024x1024", "1024x1536", "1536x1024").contains(size)) throw new IllegalArgumentException("图片尺寸无效");
                var message = WorkflowChatInputMedia.userMessage(prompt, context, media.getIfAvailable());
                List<ImageContent> images = message.contents().stream().filter(ImageContent.class::isInstance)
                        .map(ImageContent.class::cast).toList();
                if (("EDIT".equals(mode) && images.size() != 1) || ("GENERATE".equals(mode) && !images.isEmpty())) {
                    throw new IllegalArgumentException("EDIT".equals(mode) ? "图片编辑需要且仅支持一个已授权的图片附件" : "图片生成不接受源图，请选择图片编辑模式");
                }
                if (input.has("sourceImage") || input.has("sourceImages") || input.has("sourceImageUrls")) {
                    throw new IllegalArgumentException("不接受源图网址或文件路径，请使用分享聊天的已授权图片附件");
                }
                List<String> sources = images.stream().map(image -> {
                    var value = image.image();
                    if (value.base64Data() == null || !"image/jpeg".equals(value.mimeType())) {
                        throw new IllegalArgumentException("图片附件未经过安全解析");
                    }
                    return "data:image/jpeg;base64," + value.base64Data();
                }).toList();
                String text = message.contents().stream().filter(TextContent.class::isInstance)
                        .map(TextContent.class::cast).map(TextContent::text).findFirst().orElseThrow();
                var invocation = new ImageCapabilityInvocation(capability, "EDIT".equals(mode) ? "image_edit" : "text_to_image",
                        text, null, sources, null, size, 1, "wf_" + context.nodeRunId(), null, null, Map.of());
                SecurityContext previousSecurity = SecurityContextHolder.getContext();
                CallerContext previousCaller = CallerContextHolder.get();
                try {
                    SecurityContext refreshed = principals.resolve(context)
                            .orElseThrow(() -> new IllegalArgumentException("执行主体已失效，未调用图片模型"));
                    if (refreshed.getAuthentication() == null
                            || !(refreshed.getAuthentication().getPrincipal() instanceof CallerContext caller)
                            || !String.valueOf(context.tenantId()).equals(caller.getTenantId())) {
                        throw new IllegalArgumentException("执行主体租户不匹配，未调用图片模型");
                    }
                    SecurityContextHolder.setContext(refreshed);
                    CallerContextHolder.set(caller);
                    ModelRuntimeSpec resolved = runtime.resolve(model.modelId(), invocation);
                    if (!Objects.equals(resolved.modelId(), model.modelId()) || resolved.modelRevision() != model.revision()) {
                        throw new IllegalArgumentException("图片模型版本已变化，未调用模型");
                    }
                    context.cancellation().throwIfCancellationRequested();
                    var result = runtime.execute(withoutRetries(resolved), invocation);
                    context.cancellation().throwIfCancellationRequested();
                    if (result == null || result.value() == null || result.value().imageUrls().size() != 1) {
                        throw new IllegalStateException("图片模型未返回唯一图片，结果需人工确认");
                    }
                    String url = result.value().imageUrls().get(0);
                    URI uri = URI.create(url);
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || url.length() > 8192) {
                        throw new IllegalStateException("图片模型返回的结果地址无效");
                    }
                    ObjectNode output = JsonNodeFactory.instance.objectNode();
                    output.putArray("images").add(url);
                    output.put("imageUrl", url);
                    output.put("capability", capability);
                    return new WorkflowNodeResult(output, Map.of("imageCount", 1), "COMMITTED");
                } finally {
                    CallerContextHolder.set(previousCaller);
                    SecurityContextHolder.setContext(previousSecurity);
                }
            }
        };
    }

    private static int revision(AiModelConfig model) {
        long revision = model.getRevision() == null ? 1 : model.getRevision();
        if (revision < 1 || revision > Integer.MAX_VALUE) throw new IllegalArgumentException("图片模型版本无效");
        return (int) revision;
    }

    private static ModelRuntimeSpec withoutRetries(ModelRuntimeSpec runtime) {
        RuntimePolicySpec policy = runtime.runtimePolicy();
        return new ModelRuntimeSpec(runtime.modelId(), runtime.modelCode(), runtime.modelRevision(), runtime.connectionId(),
                runtime.connectionRevision(), runtime.providerCode(), runtime.protocolCode(), runtime.networkMode(), runtime.baseUrl(),
                runtime.modelName(), runtime.capabilityCode(), runtime.invocationParameters(), runtime.featureParameters(),
                runtime.activeFeatures(), runtime.providerExtraConfig(), runtime.credentials(),
                new RuntimePolicySpec(policy.maxConcurrency(), policy.connectTimeoutMs(), Math.min(120000, policy.readTimeoutMs()),
                        0, policy.qpsLimit(), policy.priority()), runtime.schemaVersion(), runtime.schemaHash(), runtime.runtimeDefinitionHash());
    }

    private static WorkflowNodeDescriptor imageDescriptor() {
        ObjectNode config = objectSchema();
        ObjectNode properties = config.putObject("properties");
        properties.putObject("mode").put("type", "string").put("title", "图片模式").put("default", "GENERATE")
                .putArray("enum").add("GENERATE").add("EDIT");
        properties.putObject("size").put("type", "string").put("title", "图片尺寸").put("default", "1024x1024")
                .putArray("enum").add("1024x1024").add("1024x1536").add("1536x1024");
        config.put("additionalProperties", false);
        ObjectNode input = objectSchema();
        input.putArray("required").add("prompt");
        input.putObject("properties").putObject("prompt").put("type", "string").put("title", "图片描述")
                .put("minLength", 1).put("maxLength", 8000)
                .put("description", "编辑模式请映射分享聊天的当前消息（包含服务端授权的图片附件引用）；每次仅生成一张图片");
        input.put("additionalProperties", false);
        ObjectNode output = objectSchema();
        var outputs = output.putObject("properties");
        outputs.putObject("images").put("type", "array").put("minItems", 1).put("maxItems", 1)
                .putObject("items").put("type", "string");
        outputs.putObject("imageUrl").put("type", "string");
        outputs.putObject("capability").put("type", "string");
        output.putArray("required").add("images").add("imageUrl").add("capability");
        output.put("additionalProperties", false);
        return new WorkflowNodeDescriptor("image", "1.0", "图片生成/编辑", "ai", config, input, output,
                WorkflowSideEffect.WRITE, Set.of("IMAGE_MODEL"), Set.of(WorkflowNodeCapability.CANCELLABLE));
    }

    private static ObjectNode objectSchema() {
        return JsonNodeFactory.instance.objectNode().put("type", "object");
    }
}
