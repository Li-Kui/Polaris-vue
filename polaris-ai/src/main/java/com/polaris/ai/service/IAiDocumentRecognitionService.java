package com.polaris.ai.service;

import com.polaris.ai.dto.DocumentRecognitionResult;

import java.util.List;

/**
 * AI 对话文档自动识别与动态挂载服务接口
 */
public interface IAiDocumentRecognitionService {

    /**
     * 提取消息中的显式提及文本（包含 @文档名 与 《文档名》）
     *
     * @param userInput 用户输入文本
     * @return 识别出的文档名称列表
     */
    List<String> extractMentions(String userInput);

    /**
     * 自动识别、匹配与动态挂载相关文档
     *
     * @param userInput            用户输入
     * @param userId               当前登录用户 ID
     * @param deptId               当前登录用户部门 ID
     * @param boundKnowledgeBaseId 会话固定绑定的知识库 ID（可为 null）
     * @return 识别挂载结果及隔离格式化 Prompt
     */
    DocumentRecognitionResult recognizeAndMount(String userInput, Long userId, Long deptId, Long boundKnowledgeBaseId);
}
