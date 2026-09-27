package com.polaris.ai.runtime.chat;

import com.polaris.ai.runtime.CapabilityInvocation;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Objects;

/** 强类型 Chat 调用；日志表示不得输出消息正文。 */
public record ChatCapabilityInvocation(ChatRequest request)
        implements CapabilityInvocation {

    public static final String CAPABILITY_CODE = "CHAT_COMPLETION";

    public ChatCapabilityInvocation {
        request = Objects.requireNonNull(request, "request");
    }

    @Override
    public String capabilityCode() {
        return CAPABILITY_CODE;
    }

    @Override
    public String toString() {
        return "ChatCapabilityInvocation[request=<redacted>]";
    }
}
