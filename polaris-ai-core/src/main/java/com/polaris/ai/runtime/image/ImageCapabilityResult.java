package com.polaris.ai.runtime.image;

import java.util.List;

/** 图片调用标准结果。 */
public record ImageCapabilityResult(List<String> imageUrls) {

    public ImageCapabilityResult {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
