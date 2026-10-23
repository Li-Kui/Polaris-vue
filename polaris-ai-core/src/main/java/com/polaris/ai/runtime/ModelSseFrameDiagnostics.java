package com.polaris.ai.runtime;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** QA 专用的有界帧分类；仅瞬时检查已解析 SSE data，不保留字段值、字段名或错误。 */
public final class ModelSseFrameDiagnostics {
    private static final int MAX_FRAME_CHARACTERS = 32768;
    private static final int MAX_TOTAL_CHARACTERS = 4194304;
    private static final int MAX_FRAMES = 20000;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .disable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
            .disable(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .recyclerPool(JsonRecyclerPools.nonRecyclingPool())
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_FRAME_CHARACTERS).maxNumberLength(128).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private long evaluatedFrames, textFrames, reasoningFrames, roleFrames, toolFrames;
    private long finishFrames, usageFrames, doneFrames, otherFrames, invalidFrames;
    private long oversizedFrames, budgetSkippedFrames;
    private int inspectedCharacters;

    ModelSseFrameDiagnostics() { }

    public record Snapshot(int version, long evaluatedFrames, long textFrames, long reasoningFrames,
                           long roleFrames, long toolFrames, long finishFrames, long usageFrames,
                           long doneFrames, long otherFrames, long invalidFrames,
                           long oversizedFrames, long budgetSkippedFrames) { }

    // 调用方持有单次 ModelHttpDiagnostics 锁，冻结与分类原子化，不改变原事件传递。
    void accept(String data) {
        if (evaluatedFrames >= MAX_FRAMES || budgetSkippedFrames > 0) {
            budgetSkippedFrames = increment(budgetSkippedFrames);return;
        }
        int length = data == null ? 0 : data.length();
        if (length > MAX_FRAME_CHARACTERS) { oversizedFrames = increment(oversizedFrames);return; }
        if (length > MAX_TOTAL_CHARACTERS - inspectedCharacters) {
            budgetSkippedFrames = increment(budgetSkippedFrames);return;
        }
        inspectedCharacters += length;evaluatedFrames++;
        if (data == null || data.isBlank()) { otherFrames++;return; }
        if ("[DONE]".equals(data.trim())) { doneFrames++;return; }
        try {
            JsonNode root = JSON.readTree(data);
            if (root == null || !root.isObject()) { invalidFrames++;return; }
            boolean text = false, reasoning = false, role = false, tool = false, finish = false;
            JsonNode choices = root.path("choices");
            if (choices.isArray()) {
                for (JsonNode choice : choices) {
                    JsonNode delta = choice.path("delta");
                    text |= nonemptyString(delta.path("content"));
                    reasoning |= nonemptyString(delta.path("reasoning_content"));
                    role |= nonemptyString(delta.path("role"));
                    tool |= (delta.path("tool_calls").isArray() && !delta.path("tool_calls").isEmpty())
                            || delta.path("function_call").isObject();
                    finish |= nonemptyString(choice.path("finish_reason"));
                }
            }
            boolean usage = root.path("usage").isObject();
            if (text) textFrames++;
            if (reasoning) reasoningFrames++;
            if (role) roleFrames++;
            if (tool) toolFrames++;
            if (finish) finishFrames++;
            if (usage) usageFrames++;
            if (!(text || reasoning || role || tool || finish || usage)) otherFrames++;
        } catch (RuntimeException ignored) {
            // 不复制或记录解析异常：异常可能带原始 data；分类失败不阻断 SDK。
            invalidFrames++;
        }
    }

    Snapshot snapshot() {
        return new Snapshot(1, evaluatedFrames, textFrames, reasoningFrames, roleFrames, toolFrames,
                finishFrames, usageFrames, doneFrames, otherFrames, invalidFrames, oversizedFrames,
                budgetSkippedFrames);
    }

    private boolean nonemptyString(JsonNode value) { return value.isString() && !value.stringValue().isEmpty(); }
    private long increment(long count) { return Math.min(1_000_000_000L, count + 1); }
}
