package com.polaris.ai.image;

import com.polaris.ai.runtime.ModelRuntimeSpec;

/** 图片 Provider 实现读取运行时的集中入口，避免重新依赖数据库 Entity。 */
public final class ImageRuntimeValues {

    private ImageRuntimeValues() {
    }

    public static String provider(ImageGenRequest request) {
        return required(request).providerCode();
    }

    public static String baseUrl(ImageGenRequest request) {
        return required(request).baseUrl();
    }

    public static String modelName(ImageGenRequest request) {
        return required(request).modelName();
    }

    public static String apiKey(ImageGenRequest request) {
        Object value = required(request).credentials().get("apiKey");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("PROVIDER_CREDENTIAL_REQUIRED");
        }
        return value.toString();
    }

    public static boolean relay(ImageGenRequest request) {
        return "RELAY".equalsIgnoreCase(required(request).networkMode());
    }

    public static String defaultImageSize(ImageGenRequest request) {
        Object value = required(request).invocationParameters().get("size");
        return value == null ? null : value.toString();
    }

    public static int connectTimeout(ImageGenRequest request) {
        return required(request).runtimePolicy().connectTimeoutMs();
    }

    public static int readTimeout(ImageGenRequest request) {
        return required(request).runtimePolicy().readTimeoutMs();
    }

    private static ModelRuntimeSpec required(ImageGenRequest request) {
        if (request == null || request.getRuntime() == null) {
            throw new IllegalArgumentException("IMAGE_RUNTIME_REQUIRED");
        }
        return request.getRuntime();
    }
}
