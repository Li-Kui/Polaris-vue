package com.polaris.ai.customer.application;

import com.polaris.ai.dto.KnowledgeCitation;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 平台客服持久化与 AI 实现之间的受限边界，不允许指定工具或外部工作流。 */
public interface CustomerServiceAiGateway {

    /** 只读本地元数据，不请求供应商、不执行检索。 */
    String validate(CustomerServiceConfig config);

    void requireAvailable(CustomerServiceConfig config, String modelHash);

    Answer answer(CustomerServiceConfig config, String modelHash,
                  List<History> history, String question, BooleanSupplier active, Consumer<NormalizedUsage> usage);

    /** 只释放已完成内容与来源检查的句段；不支持流式的实现仍只调用一次完整回答。 */
    default Answer answerStream(CustomerServiceConfig config, String modelHash, List<History> history,
            String question, BooleanSupplier active, Consumer<NormalizedUsage> usage, Consumer<AnswerPart> parts) {
        return answer(config, modelHash, history, question, active, usage);
    }

    /** 只识别受控能力与本轮新增参数，不执行工具；模型结果仍需平台重新校验。 */
    RouteDecision route(CustomerServiceConfig config, String modelHash, List<RouteCapability> capabilities,
                        RoutePending pending, String question, BooleanSupplier active, Consumer<NormalizedUsage> usage);

    Source preview(KnowledgeCitation citation);

    List<SourceLabel> sources(List<KnowledgeCitation> citations);

    record History(String question, String answer) { }

    record AnswerPart(String text, List<KnowledgeCitation> citations) {
        public AnswerPart { citations = List.copyOf(citations); }
    }

    record RouteParameter(String name, String type, boolean required, String label, String description, JsonNode example, JsonNode constraints) {
        public RouteParameter(String name, String type, boolean required) { this(name, type, required, name, "", null, null); }
    }
    record RouteCapability(String code, String name, String description, List<RouteParameter> inputs) { }
    record RouteIssue(String name, String label, String message) { }
    record RoutePending(String capabilityCode, JsonNode input, List<RouteIssue> issues) {
        public RoutePending(String capabilityCode, JsonNode input) { this(capabilityCode, input, List.of()); }
    }
    record RouteDecision(String kind, String capabilityCode, JsonNode input) { }

    record Answer(String text, List<KnowledgeCitation> citations, NormalizedUsage usage) {
        public Answer {
            citations = List.copyOf(citations);
            usage = usage == null ? NormalizedUsage.unknown() : usage;
        }
    }

    record Source(String citationId, String documentName, String text, boolean truncated,
                  List<String> locations) { }

    record SourceLabel(String citationId, String documentName) { }
}
