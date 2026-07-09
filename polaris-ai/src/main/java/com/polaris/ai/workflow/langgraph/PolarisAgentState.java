package com.polaris.ai.workflow.langgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.HashMap;
import java.util.Map;

/**
 * LangGraph4j 图工作流的共享状态定义
 * <p>
 * 所有字段均为可序列化类型，不可存放 SseEmitter 等不可序列化对象。
 * SseEmitter 通过引擎层闭包注入 NodeAction，不参与 Checkpoint 序列化。
 *
 * @author polaris
 */
public class PolarisAgentState extends AgentState {

    /** 用户原始输入 */
    public static final String USER_INPUT = "userInput";
    /** 最新节点输出（覆盖写） */
    public static final String LATEST_OUTPUT = "latestOutput";
    /** 条件路由决策值 */
    public static final String ROUTE_DECISION = "routeDecision";
    /** 节点间共享变量 */
    public static final String VARIABLES = "variables";
    /** 循环计数器（防死循环） */
    public static final String ITERATION_COUNT = "iterationCount";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        USER_INPUT,       Channels.base(() -> ""),
        LATEST_OUTPUT,    Channels.base(() -> ""),
        ROUTE_DECISION,   Channels.base(() -> ""),
        VARIABLES,        Channels.base(() -> new HashMap<String, Object>()),
        ITERATION_COUNT,  Channels.base(() -> 0)
    );

    public PolarisAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public String userInput() {
        return this.<String>value(USER_INPUT).orElse("");
    }

    public String latestOutput() {
        return this.<String>value(LATEST_OUTPUT).orElse("");
    }

    public String routeDecision() {
        return this.<String>value(ROUTE_DECISION).orElse("");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> variables() {
        return this.<Map<String, Object>>value(VARIABLES).orElse(new HashMap<>());
    }

    public int iterationCount() {
        return this.<Integer>value(ITERATION_COUNT).orElse(0);
    }
}
