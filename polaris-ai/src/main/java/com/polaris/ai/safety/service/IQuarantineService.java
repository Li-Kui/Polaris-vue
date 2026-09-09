package com.polaris.ai.safety.service;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.safety.dto.ModerationResult;

import java.nio.file.Path;

/**
 * AI 文档安全隔离服务层接口
 *
 * @author polaris
 */
public interface IQuarantineService {

    /**
     * 将高风险文档移入安全隔离区并更新数据库状态
     */
    void quarantine(AiDocument doc, ModerationResult moderation, Path sourceFile);
}
