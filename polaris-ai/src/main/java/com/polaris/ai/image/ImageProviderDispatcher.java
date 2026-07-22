package com.polaris.ai.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 绘图厂商适配器派发器：按 provider 选择匹配适配器，无匹配时走兜底适配器
 *
 * @author polaris
 */
@Slf4j
@Component
public class ImageProviderDispatcher {

    @Autowired(required = false)
    private List<ImageProviderAdapter> adapters;

    public String generate(ImageGenRequest request) throws Exception {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalStateException("未注册任何绘图厂商适配器");
        }
        String provider = request.getConfig() != null && request.getConfig().getProvider() != null
                ? request.getConfig().getProvider().toLowerCase() : "";

        ImageProviderAdapter matched = null;
        ImageProviderAdapter fallback = null;
        for (ImageProviderAdapter adapter : adapters) {
            if (adapter.isFallback()) {
                fallback = adapter;
            }
            if (adapter.supports(provider)) {
                matched = adapter;
                break;
            }
        }
        ImageProviderAdapter chosen = matched != null ? matched : fallback;
        if (chosen == null) {
            throw new IllegalStateException("未找到匹配的绘图适配器, provider=" + provider);
        }
        log.info(">>> [ImageDispatcher] provider={} 选用适配器 {}", provider, chosen.getClass().getSimpleName());
        return chosen.generate(request);
    }
}
