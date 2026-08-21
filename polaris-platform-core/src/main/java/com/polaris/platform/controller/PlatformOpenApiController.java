package com.polaris.platform.controller;

import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.usage.TokenUsageTracker;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.enums.ModelType;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.common.core.domain.AjaxResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 中台标准开放 API 接口（兼容 OpenAI 标准协议规范）
 */
@Slf4j
@RestController
@RequestMapping("/platform/api/v1")
public class PlatformOpenApiController {

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired
    private TokenUsageTracker usageTracker;

    @Data
    public static class OpenAiMessage {
        private String role;
        private String content;
    }

    @Data
    public static class OpenAiChatRequest {
        private String model;
        private List<OpenAiMessage> messages;
        private Boolean stream;
        private Double temperature;
        private Integer max_tokens;
    }

    /**
     * 获取可用模型列表
     */
    @GetMapping("/models")
    public Map<String, Object> listModels() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("object", "list");

        List<Map<String, Object>> data = new ArrayList<>();
        AiModelConfig chatConfig = modelFactory.getDefaultModelConfig(ModelType.CHAT);
        if (chatConfig != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", chatConfig.getModelName());
            m.put("object", "model");
            m.put("created", System.currentTimeMillis() / 1000);
            m.put("owned_by", "polaris-platform");
            data.add(m);
        }
        resp.put("data", data);
        return resp;
    }

    /**
     * 对话补全接口（支持阻塞与流式 SSE）
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chatCompletions(@RequestBody OpenAiChatRequest request, HttpServletResponse response) {
        CallerContext ctx = CallerContextHolder.get();
        String tenantId = ctx != null ? ctx.getTenantId() : null;

        // 检查配额
        if (usageTracker.isQuotaExceeded(tenantId)) {
            return AjaxResult.error(429, "租户 Token 配额已耗尽，请联系管理员充值");
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            return handleStreamingChat(request, tenantId);
        } else {
            return handleBlockingChat(request, tenantId);
        }
    }

    private SseEmitter handleStreamingChat(OpenAiChatRequest request, String tenantId) {
        SseEmitter emitter = new SseEmitter(180000L);
        CompletableFuture.runAsync(() -> {
            try {
                StreamingChatModel model = modelFactory.getDefaultStreamingModel();
                if (model == null) {
                    emitter.send(SseEmitter.event().data("{\"error\":\"未配置默认对话模型\"}"));
                    emitter.complete();
                    return;
                }

                AiAssistant assistant = AiServices.builder(AiAssistant.class)
                        .streamingChatModel(model)
                        .build();

                List<ChatMessage> chatMessages = convertMessages(request.getMessages());
                StringBuilder fullResponse = new StringBuilder();

                TokenStream tokenStream = assistant.chat(chatMessages);
                tokenStream
                        .onPartialResponse(token -> {
                            try {
                                fullResponse.append(token);
                                Map<String, Object> chunk = buildChunk(request.getModel(), token);
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onCompleteResponse(resp -> {
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                                int tokenEstimate = (int) Math.ceil((fullResponse.length() + request.getMessages().toString().length()) / 2.0);
                                usageTracker.onStreamComplete(tenantId, tokenEstimate);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onError(error -> {
                            emitter.completeWithError(error);
                        })
                        .start();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private Map<String, Object> handleBlockingChat(OpenAiChatRequest request, String tenantId) {
        StreamingChatModel model = modelFactory.getDefaultStreamingModel();
        if (model == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "未配置默认对话模型");
            return err;
        }

        AiAssistant assistant = AiServices.builder(AiAssistant.class)
                .streamingChatModel(model)
                .build();

        List<ChatMessage> chatMessages = convertMessages(request.getMessages());
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder fullResponse = new StringBuilder();

        TokenStream tokenStream = assistant.chat(chatMessages);
        tokenStream
                .onPartialResponse(fullResponse::append)
                .onCompleteResponse(resp -> future.complete(fullResponse.toString()))
                .onError(future::completeExceptionally)
                .start();

        String answerText = "";
        try {
            answerText = future.get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("阻塞请求获取回复超时或失败", e);
            answerText = fullResponse.toString();
        }

        int tokenEstimate = (int) Math.ceil((answerText.length() + request.getMessages().toString().length()) / 2.0);
        usageTracker.onStreamComplete(tenantId, tokenEstimate);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", "chatcmpl-" + UUID.randomUUID().toString());
        res.put("object", "chat.completion");
        res.put("created", System.currentTimeMillis() / 1000);
        res.put("model", request.getModel() != null ? request.getModel() : "polaris-default");

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", answerText);
        choice.put("message", msg);
        choice.put("finish_reason", "stop");

        res.put("choices", List.of(choice));

        Map<String, Object> usage = new HashMap<>();
        usage.put("total_tokens", tokenEstimate);
        res.put("usage", usage);

        return res;
    }

    private List<ChatMessage> convertMessages(List<OpenAiMessage> openAiMessages) {
        List<ChatMessage> list = new ArrayList<>();
        if (openAiMessages != null) {
            for (OpenAiMessage msg : openAiMessages) {
                if ("user".equalsIgnoreCase(msg.getRole())) {
                    list.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                    list.add(AiMessage.from(msg.getContent()));
                } else {
                    list.add(UserMessage.from(msg.getContent()));
                }
            }
        }
        return list;
    }

    private Map<String, Object> buildChunk(String modelName, String content) {
        Map<String, Object> chunk = new HashMap<>();
        chunk.put("id", "chatcmpl-" + UUID.randomUUID().toString());
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);
        chunk.put("model", modelName != null ? modelName : "polaris-default");

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        Map<String, String> delta = new HashMap<>();
        delta.put("content", content);
        choice.put("delta", delta);

        chunk.put("choices", List.of(choice));
        return chunk;
    }
}
