package com.polaris.ai.router;

import com.polaris.ai.tools.rag.ToolRetriever;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 意图识别与路由决策引擎
 * <p>
 * 当用户未显式指定智能体（agentCode）或工作流（workflowCode）时，
 * 自动分析用户输入，判定是直接走轻量 LLM 问答、挂载 Tool-RAG 动态工具，还是自动唤醒特定的 Agent / Workflow。
 *
 * @author polaris
 */
@Slf4j
@Component
public class IntentRouter {

    @Autowired
    private ToolRetriever toolRetriever;

    public enum RouteType {
        /** 纯模型问答/闲聊，无需挂载工具 */
        DIRECT_LLM,
        /** 工具调用意图，需要触发 Tool-RAG 动态注入最相关的工具 */
        TOOL_CALL,
        /** 工作流意图，需自动触发对应的工作流 */
        WORKFLOW,
        /** 专家角色意图，需自动加载对应的智能体 Prompt */
        AGENT_ROLE
    }

    @Data
    public static class RouteDecision {
        private RouteType type;
        private String targetCode;
        private String reason;
    }

    /**
     * 对用户输入进行动态意图判别并生成路由决策（方案 A：Zero-Keyword 原生 Function Calling 架构）
     *
     * @param userInput      用户当前输入的文本
     * @param conversationId 会话 ID
     * @return 路由决策对象
     */
    public RouteDecision route(String userInput, Long conversationId) {
        RouteDecision decision = new RouteDecision();
        if (userInput == null || userInput.trim().isEmpty()) {
            decision.setType(RouteType.DIRECT_LLM);
            decision.setReason("输入内容为空，走默认 LLM 问答");
            return decision;
        }

        // 利用 ToolRetriever 进行自适应置信度评估（物理消灭硬编码静态词库）
        try {
            boolean hasMatch = toolRetriever.hasMatchingTools(userInput);
            if (hasMatch) {
                decision.setType(RouteType.TOOL_CALL);
                decision.setReason("Tool-RAG 匹配到相关度 >= 5.0 的系统工具，自动激活工具装配");
                log.info(">>> [IntentRouter] Zero-Keyword 意图判定结果: TOOL_CALL (来自 Tool-RAG 自适应置信度判定)");
                return decision;
            }
        } catch (Exception e) {
            log.warn(">>> [IntentRouter] ToolRetriever 自适应评估异常: {}", e.getMessage());
        }

        // 无匹配的打分高工具，自动退回直连问答模式，避免给 LLM 无关工具干扰
        decision.setType(RouteType.DIRECT_LLM);
        decision.setReason("未检测到置信度高相关系统工具，自动切回直连大模型问答");
        log.info(">>> [IntentRouter] Zero-Keyword 意图判定结果: DIRECT_LLM (纯文本对话/无高相关工具)");
        return decision;
    }
}
