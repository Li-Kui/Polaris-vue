package com.polaris.ai.safety.service;

import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.stream.StreamingModerationSession;

/**
 * AI 敏感内容机器安全检测统一门面服务层接口
 *
 * @author polaris
 */
public interface IModerationFacade {

    String LOCAL_ENGINE_UNAVAILABLE = "LOCAL_ENGINE_UNAVAILABLE";

    /**
     * 同步执行敏感内容安全检测（本地优先 + 疑似云端复核）
     */
    ModerationResult moderate(ModerationRequest request);

    /**
     * 开启流式文本输出安全检测会话
     */
    StreamingModerationSession openStream(ModerationRequest request);

    /**
     * 开启流式文本输出安全检测会话（便捷重载）
     */
    default StreamingModerationSession openStream(com.polaris.ai.safety.model.ModerationScene scene, String resourceType, String resourceId) {
        return openStream(ModerationRequest.forStream(scene, resourceType, resourceId));
    }
}
