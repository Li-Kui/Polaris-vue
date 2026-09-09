package com.polaris.ai.safety.service;

import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.rule.DictionarySnapshot;

/**
 * AI 敏感词候选词发现与观察服务层接口
 *
 * @author polaris
 */
public interface IModerationCandidateService {

    /**
     * 针对疑似/高风险但存在差异的内容，聚类记录候选敏感词
     */
    void consider(ModerationRequest request, ModerationResult result, DictionarySnapshot snapshot);

    static IModerationCandidateService noop() {
        return (request, result, snapshot) -> {};
    }
}
