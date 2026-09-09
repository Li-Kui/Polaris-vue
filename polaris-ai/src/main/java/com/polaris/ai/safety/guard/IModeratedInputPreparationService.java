package com.polaris.ai.safety.guard;

import com.polaris.ai.safety.dto.PreparedAiInput;
import com.polaris.ai.safety.model.ModerationScene;

import java.util.List;

/**
 * AI 输入与附件文本提取前置安全准备服务层接口
 *
 * @author polaris
 */
public interface IModeratedInputPreparationService {

    /**
     * 统一对用户输入消息与关联附件进行权限校验、文本提取与前置安全检测
     */
    PreparedAiInput prepare(
            ModerationScene scene,
            String message,
            List<String> tokens,
            Long userId,
            String resourceType,
            String resourceId);
}
