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
     *
     * @param request 统一请求对象
     * @return 生成的图片 URL
     * @throws Exception 生成失败时抛出，由上层捕获落库
     */
    String generate(ImageGenRequest request) throws Exception;
}
