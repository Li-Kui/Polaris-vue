package com.polaris.ai.runtime.image;

import com.polaris.ai.runtime.CapabilityInvocation;

import java.util.List;
import java.util.Map;

/** 强类型图片调用；Capability 由业务模式显式决定。 */
public record ImageCapabilityInvocation(
        String capabilityCode,
        String generationMode,
        String prompt,
        String negativePrompt,
        List<String> sourceImageUrls,
        String maskImageUrl,
        String size,
        int count,
        String taskId,
        String expandParams,
        Integer upscaleFactor,
        Map<String, Object> overrides) implements CapabilityInvocation {

    public ImageCapabilityInvocation {
        sourceImageUrls = sourceImageUrls == null
                ? List.of() : List.copyOf(sourceImageUrls);
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        count = count < 1 ? 1 : count;
    }
}
