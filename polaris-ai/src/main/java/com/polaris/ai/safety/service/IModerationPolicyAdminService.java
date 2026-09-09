package com.polaris.ai.safety.service;

import com.polaris.ai.safety.dto.ModerationPolicyUpdateRequest;
import com.polaris.ai.safety.dto.ModerationTestRequest;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.vo.ModerationTestView;

import java.util.List;

/**
 * AI 敏感内容安全策略管理服务层接口
 *
 * @author polaris
 */
public interface IModerationPolicyAdminService {

    /**
     * 获取全部场景的有效安全策略配置
     */
    List<ModerationPolicy> listPolicies();

    /**
     * 更新指定场景的安全检测策略
     */
    ModerationPolicy updatePolicy(ModerationScene scene, ModerationPolicyUpdateRequest request, String operator);

    /**
     * 对测试文本进行实时安全检测评估
     */
    ModerationTestView testSentence(ModerationTestRequest request);
}
