package com.polaris.ai.workflow.langgraph;

import com.polaris.ai.workflow.WorkflowContext;
import com.polaris.ai.workflow.WorkflowNodeExecutor;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 将现有 {@link WorkflowNodeExecutor} SPI 适配为 LangGraph4j 的 NodeAction
 * <p>
 * 桥接两种接口签名：
 * <ul>
 *   <li>旧：void execute(WorkflowContext context)</li>
 *   <li>新：Map&lt;String, Object&gt; apply(State state)</li>
 * </ul>
 *
 * @author polaris
 */
public class WorkflowNodeExecutorAdapter {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNodeExecutorAdapter.class);

    /**
     * 将 WorkflowNodeExecutor 适配为 NodeAction
     *
     * @param executor  原有 Java 节点执行器
     * @param emitter   SSE 发射器（通过闭包传入，不进 state）
     * @return LangGraph4j 可用的 NodeAction
     */
    public static NodeAction<PolarisAgentState> adapt(
            String nodeInstanceId, WorkflowNodeExecutor executor, SseEmitter emitter) {

        return state -> {
            String nodeCode = executor.getNodeCode();
            log.info(">>> [LangGraph4j] 执行 Java 节点: {}", nodeCode);

            WorkflowContext legacyContext = new WorkflowContext(
                    state.userInput(),
                    state.latestOutput(),
                    emitter
            );
            legacyContext.getVariables().putAll(state.variables());

            executor.execute(legacyContext);

            Map<String, Object> updates = new HashMap<>();
            String currentLatest = state.latestOutput();
            String newOutput = currentLatest;
            if (legacyContext.getOutput() != null && !legacyContext.getOutput().trim().isEmpty()) {
                String nodeOutput = legacyContext.getOutput();
                newOutput = currentLatest.isEmpty() ? nodeOutput : (currentLatest + "\n\n" + nodeOutput);
            }
            newOutput = LangGraph4jEngine.capLatestOutput(newOutput, 30_000);
            updates.put(PolarisAgentState.LATEST_OUTPUT, newOutput);
            if (legacyContext.getRouteDecision() != null) {
                updates.put(PolarisAgentState.ROUTE_DECISION, legacyContext.getRouteDecision());
            }

            Map<String, Object> vars = new HashMap<>(legacyContext.getVariables());
            if (legacyContext.getOutput() != null) {
                vars.put(nodeInstanceId + "_output", legacyContext.getOutput());
            }
            updates.put(PolarisAgentState.VARIABLES, vars);

            return updates;
        };
    }
}
