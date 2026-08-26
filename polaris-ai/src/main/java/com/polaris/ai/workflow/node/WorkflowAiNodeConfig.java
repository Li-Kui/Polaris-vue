package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiAgentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.ai.workflow.application.WorkflowResourceOption;
import com.polaris.ai.workflow.spi.*;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 通过带版本工作流 SPI 实现的模型和知识库适配器。 */
@Configuration
public class WorkflowAiNodeConfig {

    @Bean
    public WorkflowResourceProvider workflowModelResourceProvider(
            AiModelConfigMapper modelMapper,
            AiModelFactory modelFactory) {
        return new WorkflowResourceProvider() {
            @Override
            public String kind() {
                return "MODEL";
            }

            @Override
            public List<String> validate(WorkflowResourceRequest request) {
                AiModelConfig model = model(request, modelMapper);
                if (model == null) return List.of("模型不存在或不属于当前租户");
                if (!"1".equals(model.getStatus())) return List.of("模型已停用");
                if (!"CHAT".equalsIgnoreCase(model.getModelType())) return List.of("模型不是CHAT类型");
                return List.of();
            }

            @Override
            public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                AiModelConfig model = model(request, modelMapper);
                if (model == null) throw new IllegalArgumentException("模型资源不存在");
                StreamingChatModel handle = modelFactory.getStreamingModel(model.getId());
                Map<String, Object> attributes = new LinkedHashMap<>();
                if (model.getName() != null) attributes.put("name", model.getName());
                if (model.getModelName() != null) attributes.put("modelName", model.getModelName());
                return new ResolvedWorkflowResource(
                        kind(), request.resourceKey(), request.resourceId(), 0,
                        attributes, handle);
            }

            @Override
            public List<WorkflowResourceOption> listAvailable(
                    WorkflowResourceCatalogRequest request) {
                return modelMapper.selectWorkflowResources(request.tenantId()).stream()
                        .map(model -> {
                            WorkflowResourceRequest validationRequest = resourceRequest(
                                    request, kind(), model.getId());
                            List<String> errors = validate(validationRequest);
                            Map<String, Object> attributes = new LinkedHashMap<>();
                            put(attributes, "provider", model.getProvider());
                            put(attributes, "modelName", model.getModelName());
                            put(attributes, "modelType", model.getModelType());
                            return option(kind(), model.getId(), model.getName(),
                                    model.getModelDescription(), model.getStatus(),
                                    errors, model.getTenantId() == null, attributes);
                        })
                        .toList();
            }
        };
    }

    @Bean
    public WorkflowResourceProvider workflowKnowledgeResourceProvider(
            AiKnowledgeMapper knowledgeMapper,
            AiModelConfigMapper modelMapper,
            AiVectorStoreResolver vectorStoreResolver) {
        return new WorkflowResourceProvider() {
            @Override
            public String kind() {
                return "KNOWLEDGE_BASE";
            }

            @Override
            public List<String> validate(WorkflowResourceRequest request) {
                AiKnowledgeBase knowledge = knowledge(request, knowledgeMapper);
                if (knowledge == null) return List.of("知识库不存在或不属于当前租户");
                if (!"READY".equalsIgnoreCase(knowledge.getIndexStatus())) {
                    return List.of("知识库索引尚未就绪");
                }
                if (knowledge.getEmbeddingModelId() == null
                        || modelMapper.selectWorkflowResource(
                        request.tenantId(), knowledge.getEmbeddingModelId()) == null) {
                    return List.of("知识库向量模型不属于当前租户");
                }
                return List.of();
            }

            @Override
            public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                AiKnowledgeBase knowledge = knowledge(request, knowledgeMapper);
                if (knowledge == null) throw new IllegalArgumentException("知识库资源不存在");
                KnowledgeHandle handle = new KnowledgeHandle(
                        knowledge, vectorStoreResolver.resolve(knowledge));
                Map<String, Object> attributes = new LinkedHashMap<>();
                if (knowledge.getName() != null) attributes.put("name", knowledge.getName());
                if (knowledge.getIndexVersion() != null) {
                    attributes.put("indexVersion", knowledge.getIndexVersion());
                }
                return new ResolvedWorkflowResource(
                        kind(), request.resourceKey(), request.resourceId(), 0,
                        attributes,
                        handle);
            }

            @Override
            public List<WorkflowResourceOption> listAvailable(
                    WorkflowResourceCatalogRequest request) {
                return knowledgeMapper.selectWorkflowResources(request.tenantId()).stream()
                        .filter(knowledge -> java.util.Objects.equals(
                                knowledge.getTenantId(), request.tenantId()))
                        .map(knowledge -> {
                            WorkflowResourceRequest validationRequest = resourceRequest(
                                    request, kind(), knowledge.getId());
                            List<String> errors = validate(validationRequest);
                            Map<String, Object> attributes = new LinkedHashMap<>();
                            put(attributes, "indexStatus", knowledge.getIndexStatus());
                            put(attributes, "indexVersion", knowledge.getIndexVersion());
                            put(attributes, "embeddingModelId", knowledge.getEmbeddingModelId());
                            return option(kind(), knowledge.getId(), knowledge.getName(),
                                    knowledge.getDescription(), knowledge.getIndexStatus(),
                                    errors, knowledge.getTenantId() == null, attributes);
                        })
                        .toList();
            }
        };
    }

    @Bean
    public WorkflowResourceProvider workflowAgentResourceProvider(
            AiAgentMapper agentMapper,
            AiModelConfigMapper modelMapper,
            AiModelFactory modelFactory) {
        return new WorkflowResourceProvider() {
            @Override
            public String kind() {
                return "AGENT";
            }

            @Override
            public List<String> validate(WorkflowResourceRequest request) {
                AiAgent agent = agent(request, agentMapper);
                if (agent == null) return List.of("智能体不存在或不属于当前租户");
                if (!"1".equals(agent.getStatus())) return List.of("智能体已停用");
                AiModelConfig model = agentModel(agent, request.tenantId(), modelMapper);
                if (model == null || !"1".equals(model.getStatus())) {
                    return List.of("智能体未关联当前作用域内的可用聊天模型");
                }
                return List.of();
            }

            @Override
            public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                AiAgent agent = agent(request, agentMapper);
                if (agent == null) throw new IllegalArgumentException("智能体资源不存在");
                AiModelConfig modelConfig = agentModel(agent, request.tenantId(), modelMapper);
                if (modelConfig == null) {
                    throw new IllegalArgumentException("智能体模型资源不存在");
                }
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("agentCode", agent.getAgentCode());
                attributes.put("agentName", agent.getAgentName());
                attributes.put("modelConfigId", modelConfig.getId());
                return new ResolvedWorkflowResource(
                        kind(), request.resourceKey(), request.resourceId(), 0,
                        attributes, new AgentHandle(
                                agent, modelFactory.getStreamingModel(modelConfig.getId())));
            }

            @Override
            public List<WorkflowResourceOption> listAvailable(
                    WorkflowResourceCatalogRequest request) {
                return agentMapper.selectWorkflowResources(request.tenantId()).stream()
                        .filter(agent -> java.util.Objects.equals(
                                agent.getTenantId(), request.tenantId()))
                        .map(agent -> {
                            WorkflowResourceRequest validationRequest = resourceRequest(
                                    request, kind(), agent.getId());
                            List<String> errors = validate(validationRequest);
                            Map<String, Object> attributes = new LinkedHashMap<>();
                            AiModelConfig resolvedModel = agentModel(
                                    agent, request.tenantId(), modelMapper);
                            put(attributes, "agentCode", agent.getAgentCode());
                            put(attributes, "modelConfigId",
                                    resolvedModel == null ? agent.getModelConfigId() : resolvedModel.getId());
                            put(attributes, "modelName",
                                    resolvedModel == null ? agent.getModelName() : resolvedModel.getModelName());
                            attributes.put("hasTools",
                                    agent.getTools() != null && !agent.getTools().isBlank());
                            if (agent.getTools() != null && !agent.getTools().isBlank()) {
                                attributes.put("toolNotice", "当前节点使用智能体提示词和模型，不自动调用其业务工具");
                            }
                            return option(kind(), agent.getId(), agent.getAgentName(),
                                    agent.getRemark(), agent.getStatus(), errors,
                                    agent.getTenantId() == null, attributes);
                        })
                        .toList();
            }
        };
    }

    @Bean
    public WorkflowNodeHandler llmWorkflowNodeHandler(ObjectMapper objectMapper) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "llm", "1.0", "大模型调用", "ai",
                objectSchema(Map.of(
                        "systemPrompt", stringSchema(),
                        "maxWaitSeconds", integerSchema(1, 600))),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.READ,
                Set.of("MODEL"),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) throws Exception {
                ResolvedWorkflowResource resource = firstResource(context, "MODEL");
                if (!(resource.handle() instanceof StreamingChatModel model)) {
                    throw new IllegalArgumentException("LLM节点缺少MODEL资源");
                }
                String prompt = prompt(context.input());
                List<ChatMessage> messages = new ArrayList<>();
                String systemPrompt = context.config().path("systemPrompt").asText();
                if (!systemPrompt.isBlank()) messages.add(SystemMessage.from(systemPrompt));
                messages.add(UserMessage.from(prompt));
                int waitSeconds = context.config().path("maxWaitSeconds").asInt(300);
                ChatCallResult response = chat(model, messages, context, waitSeconds);
                ObjectNode result = objectMapper.createObjectNode();
                result.put("text", response.text());
                return new WorkflowNodeResult(result, response.usage(), "NONE");
            }
        };
    }

    @Bean
    public WorkflowNodeHandler agentWorkflowNodeHandler(ObjectMapper objectMapper) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "agent", "1.0", "AI智能体", "ai",
                objectSchema(Map.of(
                        "additionalSystemPrompt", stringSchema(),
                        "maxWaitSeconds", integerSchema(1, 600))),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.READ,
                Set.of("AGENT"),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) throws Exception {
                ResolvedWorkflowResource resource = firstResource(context, "AGENT");
                if (!(resource.handle() instanceof AgentHandle handle)) {
                    throw new IllegalArgumentException("智能体节点缺少AGENT资源");
                }
                List<ChatMessage> messages = new ArrayList<>();
                String systemPrompt = handle.agent().getSystemPrompt();
                String additional = context.config().path("additionalSystemPrompt").asText();
                if (systemPrompt != null && !systemPrompt.isBlank()) {
                    messages.add(SystemMessage.from(systemPrompt));
                }
                if (!additional.isBlank()) {
                    messages.add(SystemMessage.from(additional));
                }
                messages.add(UserMessage.from(prompt(context.input())));
                ChatCallResult response = chat(
                        handle.model(), messages, context,
                        context.config().path("maxWaitSeconds").asInt(300));
                ObjectNode result = objectMapper.createObjectNode();
                result.put("text", response.text());
                result.put("agentCode", handle.agent().getAgentCode());
                result.put("agentName", handle.agent().getAgentName());
                return new WorkflowNodeResult(result, response.usage(), "NONE");
            }
        };
    }

    @Bean
    public WorkflowNodeHandler llmClassifierWorkflowNodeHandler(ObjectMapper objectMapper) {
        ObjectNode branchItem = JsonNodeFactory.instance.objectNode();
        branchItem.put("type", "object");
        branchItem.putArray("required").add("slug").add("description");
        ObjectNode branchProperties = branchItem.putObject("properties");
        branchProperties.putObject("slug").put("type", "string")
                .put("pattern", "^[a-z][a-z0-9_-]{0,63}$");
        branchProperties.putObject("description").put("type", "string")
                .put("minLength", 1).put("maxLength", 500);
        branchItem.put("additionalProperties", false);
        ObjectNode branches = JsonNodeFactory.instance.objectNode();
        branches.put("type", "array");
        branches.put("minItems", 2);
        branches.put("maxItems", 20);
        branches.set("items", branchItem);
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "llm_classifier", "1.0", "大模型语义分类", "ai",
                requiredObjectSchema(Map.of(
                        "branches", branches,
                        "systemPrompt", stringSchema(),
                        "maxWaitSeconds", integerSchema(1, 600)), "branches"),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.READ,
                Set.of("MODEL"),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) throws Exception {
                ResolvedWorkflowResource resource = firstResource(context, "MODEL");
                if (!(resource.handle() instanceof StreamingChatModel model)) {
                    throw new IllegalArgumentException("语义分类节点缺少MODEL资源");
                }
                JsonNode branchConfig = context.config().path("branches");
                Set<String> allowed = new java.util.LinkedHashSet<>();
                branchConfig.forEach(item -> allowed.add(item.path("slug").asText()));
                String instruction = "你是工作流语义分类器。只能返回JSON对象，格式为"
                        + "{\"branch\":\"稳定分支slug\",\"confidence\":0到1,\"summary\":\"简短依据\"}。"
                        + "branch必须来自以下配置：" + branchConfig;
                String custom = context.config().path("systemPrompt").asText();
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(SystemMessage.from(custom.isBlank()
                        ? instruction : custom + "\n" + instruction));
                messages.add(UserMessage.from(prompt(context.input())));
                ChatCallResult response = chat(model, messages, context,
                        context.config().path("maxWaitSeconds").asInt(300));
                JsonNode parsed = parseModelJson(objectMapper, response.text());
                String branch = parsed.path("branch").asText();
                if (!allowed.contains(branch)) {
                    throw new IllegalStateException("大模型返回了未配置的分类分支");
                }
                double confidence = parsed.path("confidence").asDouble(-1);
                if (confidence < 0 || confidence > 1) {
                    throw new IllegalStateException("大模型分类置信度格式无效");
                }
                ObjectNode result = objectMapper.createObjectNode();
                result.put("branch", branch);
                result.put("confidence", confidence);
                result.put("summary", parsed.path("summary").asText());
                return new WorkflowNodeResult(result, response.usage(), "NONE");
            }
        };
    }

    @Bean
    public WorkflowNodeHandler knowledgeRagWorkflowNodeHandler(ObjectMapper objectMapper) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "knowledge_rag", "1.0", "知识库检索", "ai",
                objectSchema(Map.of(
                        "topK", integerSchema(1, 50),
                        "minScore", numberSchema(0, 1))),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                WorkflowSideEffect.READ,
                Set.of("KNOWLEDGE_BASE"),
                Set.of(WorkflowNodeCapability.CANCELLABLE,
                        WorkflowNodeCapability.RETRYABLE,
                        WorkflowNodeCapability.CHECKPOINT_SAFE));
        return new WorkflowNodeHandler() {
            @Override
            public WorkflowNodeDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public WorkflowNodeResult execute(WorkflowNodeContext context) {
                ResolvedWorkflowResource resource = firstResource(context, "KNOWLEDGE_BASE");
                if (!(resource.handle() instanceof KnowledgeHandle handle)) {
                    throw new IllegalArgumentException("知识库节点缺少KNOWLEDGE_BASE资源");
                }
                context.cancellation().throwIfCancellationRequested();
                String query = prompt(context.input());
                var embedding = handle.vectorContext().embeddingModel().embed(query).content();
                int topK = context.config().path("topK").asInt(
                        handle.knowledge().getRetrievalTopK() == null
                                ? 5 : handle.knowledge().getRetrievalTopK());
                double minScore = context.config().path("minScore").asDouble(
                        handle.knowledge().getRetrievalMinScore() == null
                                ? 0.5 : handle.knowledge().getRetrievalMinScore());
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(Math.max(1, Math.min(topK, 50)))
                        .minScore(Math.max(0, Math.min(minScore, 1)))
                        .filter(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                                .metadataKey("knowledge_base_id")
                                .isEqualTo(handle.knowledge().getId().toString()))
                        .build();
                List<EmbeddingMatch<TextSegment>> matches = handle.vectorContext()
                        .embeddingStore().search(request).matches();
                var items = objectMapper.createArrayNode();
                if (matches != null) {
                    for (EmbeddingMatch<TextSegment> match : matches) {
                        ObjectNode item = items.addObject();
                        item.put("score", match.score());
                        item.put("text", match.embedded().text());
                        var metadata = match.embedded().metadata();
                        if (metadata != null) {
                            item.put("documentId", metadata.getString("doc_id"));
                            item.put("documentName", metadata.getString("doc_name"));
                        }
                    }
                }
                ObjectNode result = objectMapper.createObjectNode();
                result.set("matches", items);
                return WorkflowNodeResult.success(result);
            }
        };
    }

    private static AiModelConfig model(
            WorkflowResourceRequest request, AiModelConfigMapper mapper) {
        try {
            return mapper.selectWorkflowResource(
                    request.tenantId(), Long.parseLong(request.resourceId()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static WorkflowResourceRequest resourceRequest(
            WorkflowResourceCatalogRequest request, String kind, Long resourceId) {
        return new WorkflowResourceRequest(
                request.tenantId(), request.environment(), kind, "catalog",
                String.valueOf(resourceId), request.principalType(), request.principalId());
    }

    private static WorkflowResourceOption option(
            String kind,
            Long resourceId,
            String name,
            String description,
            String status,
            List<String> errors,
            boolean shared,
            Map<String, Object> attributes) {
        List<String> safeErrors = errors == null ? List.of() : errors;
        return new WorkflowResourceOption(
                kind, String.valueOf(resourceId), name, description, status,
                safeErrors.isEmpty(), safeErrors.isEmpty() ? null : String.join("；", safeErrors),
                shared, Map.copyOf(attributes));
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static AiKnowledgeBase knowledge(
            WorkflowResourceRequest request, AiKnowledgeMapper mapper) {
        try {
            return mapper.selectWorkflowResource(
                    request.tenantId(), Long.parseLong(request.resourceId()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AiAgent agent(
            WorkflowResourceRequest request, AiAgentMapper mapper) {
        try {
            AiAgent agent = mapper.selectWorkflowResource(
                    request.tenantId(), Long.parseLong(request.resourceId()));
            return agent != null && java.util.Objects.equals(
                    agent.getTenantId(), request.tenantId()) ? agent : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AiModelConfig agentModel(
            AiAgent agent, Long tenantId, AiModelConfigMapper mapper) {
        if (agent == null) return null;
        if (agent.getModelConfigId() != null) {
            AiModelConfig configured = mapper.selectWorkflowResource(
                    tenantId, agent.getModelConfigId());
            if (configured != null && "CHAT".equalsIgnoreCase(configured.getModelType())) {
                return configured;
            }
        }
        if (agent.getModelName() == null || agent.getModelName().isBlank()) return null;
        return mapper.selectWorkflowResourceByModelName(tenantId, agent.getModelName().trim());
    }

    private static ResolvedWorkflowResource firstResource(
            WorkflowNodeContext context, String kind) {
        return context.resources().values().stream()
                .filter(resource -> kind.equals(resource.kind()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少资源: " + kind));
    }

    private static String prompt(JsonNode input) {
        if (input == null || input.isNull()) return "";
        if (input.isTextual()) return input.asText();
        if (input.hasNonNull("prompt")) return input.path("prompt").asText();
        if (input.hasNonNull("query")) return input.path("query").asText();
        return input.toString();
    }

    private static ObjectNode objectSchema(Map<String, JsonNode> properties) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode propertyNode = schema.putObject("properties");
        properties.forEach(propertyNode::set);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode requiredObjectSchema(
            Map<String, JsonNode> properties, String... required) {
        ObjectNode schema = objectSchema(properties);
        var requiredNode = schema.putArray("required");
        for (String property : required) requiredNode.add(property);
        return schema;
    }

    private static ChatCallResult chat(
            StreamingChatModel model,
            List<ChatMessage> messages,
            WorkflowNodeContext context,
            int waitSeconds) throws InterruptedException {
        StringBuilder output = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ChatResponse> completed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        model.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String value) {
                context.cancellation().throwIfCancellationRequested();
                if (value != null) output.append(value);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                completed.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        });
        if (!latch.await(waitSeconds, TimeUnit.SECONDS)) {
            throw new IllegalStateException("大模型响应超时");
        }
        if (error.get() != null) {
            throw new IllegalStateException("大模型调用失败: " + error.get().getMessage());
        }
        Map<String, Number> usage = new LinkedHashMap<>();
        ChatResponse response = completed.get();
        if (response != null && response.tokenUsage() != null) {
            var tokenUsage = response.tokenUsage();
            if (tokenUsage.inputTokenCount() != null) {
                usage.put("inputTokens", tokenUsage.inputTokenCount());
            }
            if (tokenUsage.outputTokenCount() != null) {
                usage.put("outputTokens", tokenUsage.outputTokenCount());
            }
            if (tokenUsage.totalTokenCount() != null) {
                usage.put("totalTokens", tokenUsage.totalTokenCount());
            }
        }
        return new ChatCallResult(output.toString(), usage);
    }

    private static JsonNode parseModelJson(ObjectMapper objectMapper, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        try {
            JsonNode result = objectMapper.readTree(normalized);
            if (result == null || !result.isObject()) {
                throw new IllegalStateException("大模型分类结果必须是JSON对象");
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("大模型分类结果不是有效JSON", e);
        }
    }

    private static ObjectNode stringSchema() {
        return JsonNodeFactory.instance.objectNode().put("type", "string");
    }

    private static ObjectNode integerSchema(int minimum, int maximum) {
        return JsonNodeFactory.instance.objectNode()
                .put("type", "integer").put("minimum", minimum).put("maximum", maximum);
    }

    private static ObjectNode numberSchema(double minimum, double maximum) {
        return JsonNodeFactory.instance.objectNode()
                .put("type", "number").put("minimum", minimum).put("maximum", maximum);
    }

    private record KnowledgeHandle(
            AiKnowledgeBase knowledge,
            AiVectorStoreResolver.VectorContext vectorContext) {
    }

    private record AgentHandle(AiAgent agent, StreamingChatModel model) {
    }

    private record ChatCallResult(String text, Map<String, Number> usage) {
    }
}
