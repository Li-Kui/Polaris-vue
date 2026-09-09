package com.polaris.ai.safety.service;

import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;

/**
 * AI 安全策略解析服务层接口
 *
 * @author polaris
 */
public interface IModerationPolicyService {

    /**
     * 解析指定场景的有效安全策略
     */
    ModerationPolicy resolve(ModerationScene scene);
}
