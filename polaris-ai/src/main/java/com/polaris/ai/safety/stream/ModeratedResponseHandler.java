package com.polaris.ai.safety.stream;

import com.polaris.ai.domain.AiMessage;
import com.polaris.ai.safety.exception.ModerationBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ModeratedResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ModeratedResponseHandler.class);

    public interface SseSender {
        void send(String event, String data);
    }

    private final StreamingModerationSession answerSession;
    private final StreamingModerationSession reasoningSession;
    private final SseSender sseSender;
    private final AtomicBoolean isCancelled;
    private final Consumer<AiMessage> onCompleteConsumer;
    private final StringBuilder directAnswer = new StringBuilder();
    private final StringBuilder directReasoning = new StringBuilder();

    private boolean firstTokenSent = false;
    private Long conversationId;

    public ModeratedResponseHandler(
            StreamingModerationSession answerSession,
            StreamingModerationSession reasoningSession,
            SseSender sseSender,
            AtomicBoolean isCancelled,
            Consumer<AiMessage> onCompleteConsumer) {
        this.answerSession = answerSession;
        this.reasoningSession = reasoningSession;
        this.sseSender = sseSender;
        this.isCancelled = isCancelled != null ? isCancelled : new AtomicBoolean(false);
        this.onCompleteConsumer = onCompleteConsumer;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public void onToken(String token) {
        if (isCancelled.get() || (answerSession != null && answerSession.blocked())) {
            return;
        }

        if (answerSession == null) {
            sendDirectMessage(token);
            return;
        }

        try {
            List<String> releasable = answerSession.append(token);
            if (releasable != null && !releasable.isEmpty()) {
                for (String safeChunk : releasable) {
                    sendDirectMessage(safeChunk);
                }
            }
        } catch (ModerationBlockedException e) {
            handleBlocked(e);
        }
    }

    public void onThinking(String thinking) {
        if (isCancelled.get() || (reasoningSession != null && reasoningSession.blocked())) {
            return;
        }

        if (reasoningSession == null) {
            sendDirectReasoning(thinking);
            return;
        }

        try {
            List<String> releasable = reasoningSession.append(thinking);
            if (releasable != null && !releasable.isEmpty()) {
                for (String safeChunk : releasable) {
                    sendDirectReasoning(safeChunk);
                }
            }
        } catch (ModerationBlockedException e) {
            handleBlocked(e);
        }
    }

    public void onComplete(Integer totalTokens) {
        if (isCancelled.get()) {
            return;
        }
        if (answerSession != null && answerSession.blocked()) {
            return;
        }

        try {
            if (answerSession != null) {
                List<String> remaining = answerSession.finish();
                if (remaining != null && !remaining.isEmpty()) {
                    for (String safeChunk : remaining) {
                        sendDirectMessage(safeChunk);
                    }
                }
            }
            if (reasoningSession != null) {
                List<String> remainingReasoning = reasoningSession.finish();
                if (remainingReasoning != null && !remainingReasoning.isEmpty()) {
                    for (String safeChunk : remainingReasoning) {
                        sendDirectReasoning(safeChunk);
                    }
                }
            }
        } catch (ModerationBlockedException e) {
            handleBlocked(e);
            return;
        }

        String finalApproved = answerSession != null ? answerSession.approvedText() : directAnswer.toString();
        String finalReasoning = reasoningSession != null ? reasoningSession.approvedText() : directReasoning.toString();

        if (onCompleteConsumer != null) {
            AiMessage msg = new AiMessage();
            msg.setConversationId(conversationId);
            msg.setRole("assistant");
            msg.setContent(finalApproved);
            if (finalReasoning != null && !finalReasoning.isEmpty()) {
                msg.setReasoningContent(finalReasoning);
            }
            msg.setTokens(totalTokens);
            msg.setModerationStatus("SAFE");
            onCompleteConsumer.accept(msg);
        }

        if (sseSender != null) {
            sseSender.send("done", "[DONE]");
        }
    }

    private void handleBlocked(ModerationBlockedException e) {
        isCancelled.set(true);
        log.warn("AI 输出流触发安全策略已被熔断截断");

        if (sseSender != null) {
            sseSender.send("moderation_blocked",
                    "{\"scope\":\"output\",\"code\":\"AI_OUTPUT_BLOCKED\",\"message\":\"回答触发安全策略，已为您终止输出。\"}");
        }

        String partialApproved = answerSession != null ? answerSession.approvedText() : "";
        String partialReasoning = reasoningSession != null ? reasoningSession.approvedText() : "";

        if (onCompleteConsumer != null) {
            AiMessage msg = new AiMessage();
            msg.setConversationId(conversationId);
            msg.setRole("assistant");
            msg.setContent(partialApproved);
            if (partialReasoning != null && !partialReasoning.isEmpty()) {
                msg.setReasoningContent(partialReasoning);
            }
            msg.setModerationStatus("INTERRUPTED_BLOCKED");
            onCompleteConsumer.accept(msg);
        }
    }

    private void sendDirectMessage(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (!firstTokenSent) {
            firstTokenSent = true;
            if (sseSender != null) {
                sseSender.send("status", "");
            }
        }
        if (answerSession == null) {
            directAnswer.append(chunk);
        }
        String escaped = chunk.replace("\n", "__SSE_NEWLINE__");
        if (sseSender != null) {
            sseSender.send("message", escaped);
        }
    }

    private void sendDirectReasoning(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (reasoningSession == null) {
            directReasoning.append(chunk);
        }
        String escaped = chunk.replace("\n", "__SSE_NEWLINE__");
        if (sseSender != null) {
            sseSender.send("reasoning", escaped);
        }
    }
}
