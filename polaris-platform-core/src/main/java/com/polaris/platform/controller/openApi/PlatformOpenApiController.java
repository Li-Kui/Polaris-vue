package com.polaris.platform.controller.openApi;

import com.polaris.ai.chat.AiAssistant;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.enums.ModelType;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.platform.dto.OpenAiChatRequest;
import com.polaris.platform.dto.OpenAiMessage;
import com.polaris.platform.service.TokenQuotaService;
import com.polaris.platform.service.TokenQuotaService.TokenQuotaExceededException;
import com.polaris.platform.service.TokenQuotaService.TokenQuotaUnavailableException;
import com.polaris.platform.service.TokenQuotaService.TokenReservation;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 中台标准开放 API 接口（兼容 OpenAI 标准协议规范）
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台OpenAI兼容接口")
@Slf4j
@RestController
@RequestMapping("/platform/api/v1")
public class PlatformOpenApiController {

    private static final int DEFAULT_OUTPUT_TOKEN_RESERVATION = 4096;

    @Autowired
    private AiModelFactory aiModelFactory;

    @Autowired
    private TokenQuotaService tokenQuotaService;

    @Autowired
    @Qualifier("aiTaskExecutor")
    private TaskExecutor aiTaskExecutor;

    /**
     * 获取可用模型列表
     */
    @Operation(summary = "获取 OpenAI 兼容模型列表")
    @GetMapping("/models")
    public Map<String, Object> listModels() {
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("object", "list");

        List<Map<String, Object>> data = new ArrayList<>();
        AiModelConfig chatConfig = aiModelFactory.getDefaultModelConfig(ModelType.CHAT);
        if (chatConfig != null) {
            Map<String, Object> model = new HashMap<>();
            model.put("id", chatConfig.getModelName());
            model.put("object", "model");
            model.put("created", System.currentTimeMillis() / 1000);
            model.put("owned_by", "polaris-platform");
            data.add(model);
        }
        responseData.put("data", data);
        return responseData;
    }

    /**
     * 对话补全接口（支持阻塞与流式 SSE）
     */
    @Operation(summary = "OpenAI 兼容对话补全")
    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chatCompletions(@RequestBody OpenAiChatRequest request, HttpServletResponse response) {
        CallerContext callerContext = CallerContextHolder.get();
        String tenantId = callerContext != null ? callerContext.getTenantId() : null;

        TokenReservation reservation;
        try {
            reservation = tokenQuotaService.reserve(tenantId, estimateReservationTokens(request));
        } catch (TokenQuotaExceededException e) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return AjaxResult.error(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage());
        } catch (TokenQuotaUnavailableException e) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return AjaxResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(), e.getMessage());
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            return handleStreamingChat(request, reservation);
        } else {
            try {
                return handleBlockingChat(request, reservation);
            } catch (RuntimeException e) {
                tokenQuotaService.release(reservation);
                throw e;
            }
        }
    }

    private SseEmitter handleStreamingChat(OpenAiChatRequest request, TokenReservation reservation) {
        SseEmitter emitter = new SseEmitter(180000L);
        AtomicBoolean finalized = new AtomicBoolean(false);
        // 模型回调可能由第三方线程执行，配额操作只使用显式传入的预占记录
        Runnable releaseReservation = () -> {
            if (finalized.compareAndSet(false, true)) {
                tokenQuotaService.release(reservation);
            }
        };
        CompletableFuture.runAsync(() -> {
            try {
                StreamingChatModel model = aiModelFactory.getDefaultStreamingModel();
                if (model == null) {
                    releaseReservation.run();
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
                        .onCompleteResponse(response -> {
                            try {
                                int tokenEstimate = estimateActualTokens(request, fullResponse.length());
                                if (finalized.compareAndSet(false, true)) {
                                    completeReservation(reservation, tokenEstimate);
                                }
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onError(error -> {
                            releaseReservation.run();
                            emitter.completeWithError(error);
                        })
                        .start();

            } catch (Exception e) {
                releaseReservation.run();
                emitter.completeWithError(e);
            }
        }, aiTaskExecutor);
        return emitter;
    }

    private Map<String, Object> handleBlockingChat(OpenAiChatRequest request, TokenReservation reservation) {
        StreamingChatModel model = aiModelFactory.getDefaultStreamingModel();
        if (model == null) {
            tokenQuotaService.release(reservation);
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
                .onCompleteResponse(response -> future.complete(fullResponse.toString()))
                .onError(future::completeExceptionally)
                .start();

        String answerText = "";
        try {
            answerText = future.get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("阻塞请求获取回复超时或失败", e);
            answerText = fullResponse.toString();
        }

        int tokenEstimate = estimateActualTokens(request, answerText.length());
        completeReservation(reservation, tokenEstimate);

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("id", "chatcmpl-" + UUID.randomUUID().toString());
        responseData.put("object", "chat.completion");
        responseData.put("created", System.currentTimeMillis() / 1000);
        responseData.put("model", request.getModel() != null ? request.getModel() : "polaris-default");

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        Map<String, String> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", answerText);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        responseData.put("choices", List.of(choice));

        Map<String, Object> usage = new HashMap<>();
        usage.put("total_tokens", tokenEstimate);
        responseData.put("usage", usage);

        return responseData;
    }

    private void completeReservation(TokenReservation reservation, int actualTokens) {
        try {
            tokenQuotaService.complete(reservation, actualTokens);
        } catch (RuntimeException e) {
            log.error("租户[{}] Token 配额结算失败", reservation.tenantId(), e);
        }
    }

    private int estimateReservationTokens(OpenAiChatRequest request) {
        int outputTokens = request.getMaxTokens() != null && request.getMaxTokens() > 0
                ? request.getMaxTokens() : DEFAULT_OUTPUT_TOKEN_RESERVATION;
        long total = (long) estimateMessageTokens(request.getMessages()) + outputTokens;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private int estimateActualTokens(OpenAiChatRequest request, int responseLength) {
        long outputTokens = (long) Math.ceil(responseLength / 2.0);
        long total = estimateMessageTokens(request.getMessages()) + outputTokens;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, total));
    }

    private int estimateMessageTokens(List<OpenAiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long characters = 0L;
        for (OpenAiMessage message : messages) {
            if (message != null && message.getContent() != null) {
                characters += message.getContent().length();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(characters / 2.0));
    }

    private List<ChatMessage> convertMessages(List<OpenAiMessage> openAiMessages) {
        List<ChatMessage> list = new ArrayList<>();
        if (openAiMessages != null) {
            for (OpenAiMessage message : openAiMessages) {
                if ("user".equalsIgnoreCase(message.getRole())) {
                    list.add(UserMessage.from(message.getContent()));
                } else if ("assistant".equalsIgnoreCase(message.getRole())) {
                    list.add(AiMessage.from(message.getContent()));
                } else {
                    list.add(UserMessage.from(message.getContent()));
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
