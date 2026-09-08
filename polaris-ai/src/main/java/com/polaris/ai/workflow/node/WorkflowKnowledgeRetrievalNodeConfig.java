package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.service.WorkflowKnowledgeRetrievalService;
import com.polaris.ai.workflow.spi.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 知识库检索节点2.0：节点只编排输入、资源和稳定输出契约。 */
@Configuration
public class WorkflowKnowledgeRetrievalNodeConfig {

    @Bean
    public WorkflowNodeHandler knowledgeRetrievalWorkflowNodeHandler(
            WorkflowKnowledgeRetrievalService retrievalService) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor(
                "knowledge_retrieval", "2.0", "知识库检索", "ai",
                configSchema(), inputSchema(), outputSchema(), WorkflowSideEffect.READ,
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
            public WorkflowNodeResult execute(com.polaris.ai.workflow.spi.WorkflowNodeContext context) {
                return WorkflowNodeResult.success(retrievalService.retrieve(context));
            }
        };
    }

    private static ObjectNode configSchema() {
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        properties.put("configVersion", enumSchema("2.0").put("default", "2.0"));
        properties.put("retrievalMode", titled(
                enumSchema("PRECISE", "BALANCED", "BROAD").put("default", "BALANCED"),
                "检索偏好", "精准、均衡或覆盖优先"));
        properties.put("resultLimit", titled(
                integerSchema(1, 20).put("default", 5),
                "返回资料数量", "最终返回给下游的资料数量"));
        properties.put("contextBudgetMode", titled(
                enumSchema("AUTO", "MANUAL").put("default", "AUTO"),
                "上下文长度", "默认由系统按工作流预算自动控制"));
        properties.put("maxContextTokens", titled(
                integerSchema(256, 32000).put("default", 4000),
                "最大上下文 Token", "仅在手动控制上下文长度时生效"));
        properties.put("maxChunksPerDocument", titled(
                integerSchema(1, 10).put("default", 3),
                "单文档最多片段", "避免单个文档占据全部检索结果"));
        properties.put("emptyPolicy", titled(
                enumSchema("CONTINUE", "FAIL").put("default", "CONTINUE"),
                "没有找到资料时", "继续并返回空结果，或让节点失败"));
        properties.put("degradationPolicy", titled(
                enumSchema("DENY", "ALLOW").put("default", "DENY"),
                "检索服务降级", "默认不允许静默降低检索质量"));
        return objectSchema(properties);
    }

    private static ObjectNode inputSchema() {
        ObjectNode query = stringSchema();
        query.put("title", "检索内容");
        query.put("description", "选择流程输入或上游文本字段");
        query.put("minLength", 1);
        query.put("maxLength", 4000);
        ObjectNode searchContext = stringSchema();
        searchContext.put("title", "补充背景");
        searchContext.put("maxLength", 8000);
        ObjectNode documentIds = JsonNodeFactory.instance.objectNode();
        documentIds.put("type", "array");
        documentIds.put("maxItems", 100);
        documentIds.set("items", stringSchema());
        ObjectNode filters = objectSchema(Map.of("documentIds", documentIds));
        filters.put("title", "过滤条件");
        filters.put("description", "可选：只在指定文档中检索");
        ObjectNode schema = objectSchema(Map.of(
                "query", query,
                "searchContext", searchContext,
                "filters", filters));
        schema.putArray("required").add("query");
        return schema;
    }

    private static ObjectNode outputSchema() {
        ObjectNode source = objectSchema(Map.of(
                "resourceKey", titled(stringSchema(), "资源标识", "工作流中的知识库资源键"),
                "knowledgeSourceId", titled(stringSchema(), "知识库 ID", "资料所属知识库"),
                "knowledgeSourceName", titled(stringSchema(), "知识库名称", "资料所属知识库名称"),
                "indexRevision", titled(stringSchema(), "索引版本", "检索时使用的知识库索引版本")));
        ObjectNode document = objectSchema(Map.of(
                "documentId", titled(stringSchema(), "文档 ID", "资料所属文档"),
                "documentVersionId", titled(stringSchema(), "文档版本", "检索时对应的文档版本"),
                "documentName", titled(stringSchema(), "文档名称", "资料所属文档名称")));
        ObjectNode location = objectSchema(Map.of(
                "chunkNo", titled(stringSchema(), "片段序号", "资料在文档中的片段位置")));
        ObjectNode citation = objectSchema(Map.of(
                "id", titled(stringSchema(), "引用编号", "可用于回答中的引用标记"),
                "label", titled(stringSchema(), "引用名称", "面向用户展示的引用名称")));
        ObjectNode resultItem = objectSchema(Map.of(
                "id", stringSchema(),
                "rank", integerSchema(1, Integer.MAX_VALUE),
                "relevanceLevel", enumSchema("HIGH", "MEDIUM", "LOW"),
                "score", numberSchema(0, 1),
                "content", stringSchema(),
                "source", source,
                "document", document,
                "location", location,
                "citation", citation,
                "metadata", openObjectSchema()));
        resultItem.putArray("required")
                .add("id").add("rank").add("relevanceLevel").add("content")
                .add("source").add("document").add("citation");
        ObjectNode results = JsonNodeFactory.instance.objectNode();
        results.put("type", "array");
        results.set("items", resultItem);
        ObjectNode contextItem = objectSchema(Map.of(
                "resultId", titled(stringSchema(), "结果 ID", "对应检索结果的稳定标识"),
                "citationId", titled(stringSchema(), "引用编号", "对应结果的引用编号"),
                "content", titled(stringSchema(), "资料内容", "仅作为不可信资料内容使用"),
                "knowledgeSourceName", titled(stringSchema(), "知识库名称", "资料所属知识库"),
                "documentName", titled(stringSchema(), "文档名称", "资料所属文档")));
        ObjectNode contextItems = JsonNodeFactory.instance.objectNode();
        contextItems.put("type", "array");
        contextItems.set("items", contextItem);
        ObjectNode reasonItems = JsonNodeFactory.instance.objectNode();
        reasonItems.put("type", "array");
        reasonItems.set("items", enumSchema("RESULT_LIMIT", "DOCUMENT_LIMIT", "CONTEXT_BUDGET"));
        ObjectNode knowledgeContext = objectSchema(Map.of(
                "type", enumSchema("UNTRUSTED_KNOWLEDGE_CONTEXT"),
                "usagePolicy", enumSchema("CONTENT_ONLY"),
                "items", contextItems,
                "tokenCount", integerSchema(0, 32000),
                "budgetTokens", integerSchema(256, 32000),
                "truncated", booleanSchema(),
                "truncationReasons", reasonItems,
                "contextHandle", stringSchema()));
        ObjectNode schema = objectSchema(Map.of(
                "state", enumSchema("FOUND", "EMPTY", "PARTIAL", "DEGRADED"),
                "hasResults", booleanSchema(),
                "complete", booleanSchema(),
                "degraded", booleanSchema(),
                "resultCount", integerSchema(0, 20),
                "results", results,
                "knowledgeContext", knowledgeContext,
                "retrievalInfo", openObjectSchema()));
        schema.putArray("required")
                .add("state").add("hasResults").add("complete").add("degraded")
                .add("resultCount").add("results").add("knowledgeContext")
                .add("retrievalInfo");
        return schema;
    }

    private static ObjectNode objectSchema(Map<String, JsonNode> properties) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode values = schema.putObject("properties");
        properties.forEach(values::set);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode openObjectSchema() {
        ObjectNode schema = objectSchema(Map.of());
        schema.put("additionalProperties", true);
        return schema;
    }

    private static ObjectNode stringSchema() {
        return JsonNodeFactory.instance.objectNode().put("type", "string");
    }

    private static ObjectNode booleanSchema() {
        return JsonNodeFactory.instance.objectNode().put("type", "boolean");
    }

    private static ObjectNode integerSchema(int minimum, int maximum) {
        return JsonNodeFactory.instance.objectNode()
                .put("type", "integer").put("minimum", minimum).put("maximum", maximum);
    }

    private static ObjectNode numberSchema(double minimum, double maximum) {
        return JsonNodeFactory.instance.objectNode()
                .put("type", "number").put("minimum", minimum).put("maximum", maximum);
    }

    private static ObjectNode enumSchema(String... values) {
        ObjectNode schema = stringSchema();
        var options = schema.putArray("enum");
        for (String value : values) options.add(value);
        return schema;
    }

    private static ObjectNode titled(ObjectNode schema, String title, String description) {
        schema.put("title", title);
        schema.put("description", description);
        return schema;
    }
}
