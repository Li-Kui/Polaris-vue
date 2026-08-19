package com.polaris.ai.safety.service;

import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;

/**
 * AI 安全检测审计日志服务层接口
 *
 * @author polaris
 */
public interface IModerationAuditService {

    /**
     * 异步记录安全检测事件元数据（不保留用户原始完整文本）
     */
    void record(ModerationRequest request, ModerationResult result);
}
