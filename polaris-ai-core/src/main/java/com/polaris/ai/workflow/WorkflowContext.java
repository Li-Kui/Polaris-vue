package com.polaris.ai.workflow;

import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行上下文
 * 负责在工作流执行期间传递用户输入、节点间共享变量、前一节点输出并暴露流式 SSE 发射器
 *
 * @author polaris
 */
@Data
public class WorkflowContext {

    /** 用户的最原始输入指令 */
    private final String userInput;

    /** SSE 实时消息发射器 */
    private final SseEmitter emitter;

    /** 节点间共享的数据容器（使用 ConcurrentHashMap 确保工作流分支并行节点执行时的线程安全） */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /** 最近一个节点执行后的完整文本输出（作为下一个节点的输入上下文） */
    private String latestOutput;

    /** 条件路由决策值（Java 节点可设置，用于图引擎条件分支） */
    private String routeDecision;

    /** 当前节点执行后的输出（等同于 latestOutput，供适配器读取） */
    private String output;

    public WorkflowContext(String userInput, SseEmitter emitter) {
        this.userInput = userInput;
        this.emitter = emitter;
    }

    /**
     * 三参数构造函数（LangGraph4j 适配器使用）
     */
    public WorkflowContext(String userInput, String latestOutput, SseEmitter emitter) {
        this.userInput = userInput;
        this.latestOutput = latestOutput;
        this.emitter = emitter;
    }

    /**
     * 设置共享变量（兼顾 ConcurrentHashMap 不可存 null value 的特性，保持原业务逻辑一致）
     */
    public void setVariable(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            this.variables.remove(key);
        } else {
            this.variables.put(key, value);
        }
    }

    /**
     * 获取共享变量
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        if (key == null) {
            return null;
        }
        return (T) this.variables.get(key);
    }
}
