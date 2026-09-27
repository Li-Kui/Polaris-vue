package com.polaris.ai.runtime.embedding;

import com.polaris.ai.runtime.CapabilityInvocation;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;

import java.util.Objects;

/** 强类型 Embedding 调用；日志表示只暴露输入数量。 */
public record EmbeddingCapabilityInvocation(EmbeddingRequest request)
        implements CapabilityInvocation {

    public static final String CAPABILITY_CODE = "TEXT_EMBEDDING";

    public EmbeddingCapabilityInvocation {
        request = Objects.requireNonNull(request, "request");
    }

    @Override
    public String capabilityCode() {
        return CAPABILITY_CODE;
    }

    @Override
    public String toString() {
        return "EmbeddingCapabilityInvocation[inputCount="
                + request.inputs().size() + "]";
    }
}
