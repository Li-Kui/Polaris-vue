package com.polaris.ai.workflow;

import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

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

    /** 节点间共享的数据容器 */
    private final Map<String, Object> variables = new HashMap<>();

    /** 最近一个节点执行后的完整文本输出（作为下一个节点的输入上下文） */
    private String latestOutput;

    public WorkflowContext(String userInput, SseEmitter emitter) {
        this.userInput = userInput;
        this.emitter = emitter;
    }

    /**
     * 设置共享变量
     */
    public void setVariable(String key, Object value) {
        this.variables.put(key, value);
    }

    /**
     * 获取共享变量
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) this.variables.get(key);
    }
}
