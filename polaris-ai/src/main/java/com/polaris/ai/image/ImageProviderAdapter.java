package com.polaris.ai.image;

/**
 * 绘图厂商适配器接口。新增厂商只需实现本接口并交给 Spring 托管。
 *
 * @author polaris
 */
public interface ImageProviderAdapter {

    /** 是否支持该厂商标识（provider，已小写） */
    boolean supports(String provider);

    /** 当没有任何适配器匹配时，是否作为兜底适配器 */
    default boolean isFallback() { return false; }

    /**
     * 执行绘图，返回图片 URL（同步阻塞，由上层线程池驱动）
     * 单图返回 URL 字符串；多图返回 JSON 数组字符串（如 ["url1", "url2"]）
     *
     * @param request 统一请求对象
     * @return 生成的图片 URL（单图或 JSON 数组字符串）
     * @throws Exception 生成失败时抛出，由上层捕获落库
     */
    String generate(ImageGenRequest request) throws Exception;

    /**
     * 执行绘图，返回图片 URL 列表
     *
     * @param request 统一请求对象
     * @return 生成的图片 URL 列表
     * @throws Exception 生成失败时抛出
     */
    default java.util.List<String> generateList(ImageGenRequest request) throws Exception {
        String result = generate(request);
        if (result == null || result.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String str = result.trim();
        if (str.startsWith("[")) {
            try {
                java.util.List<String> list = com.alibaba.fastjson2.JSON.parseArray(str, String.class);
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            } catch (Exception ignored) {}
        }
        if (str.contains(",")) {
            return java.util.Arrays.asList(str.split(","));
        }
        return java.util.Collections.singletonList(str);
    }
}

