package com.polaris.ai.safety.provider;

/** One normalized label/confidence pair projected from the Aliyun SDK response. */
public record AliyunLabelResult(String label, Double confidence) {
}
