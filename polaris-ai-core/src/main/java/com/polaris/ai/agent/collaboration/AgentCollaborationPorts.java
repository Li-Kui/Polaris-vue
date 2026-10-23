package com.polaris.ai.agent.collaboration;

import java.util.List;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 强制注入授权、持久化与调用边界；没有内存回退实现或默认管理员身份。 */
public final class AgentCollaborationPorts {
    private AgentCollaborationPorts() { }
    public interface ModelGateway {
        void validate(ModelSelection model, List<ToolDefinition> tools);
        Plan plan(PlanningInput input, AgentCallControl control);
        Answer answer(AnswerInput input, AgentCallControl control);
    }
    public interface ToolArgumentValidator {
        void validateDefinition(String schemaJson);
        void validateArguments(String schemaJson, String argumentsJson);
    }
    public interface ExternalReadToolGateway {
        void validateBindings(Context context, List<ToolBinding> bindings);
        ToolResult invoke(ToolIntent intent, AgentCallControl control);
    }
    public interface KnowledgeGateway {
        List<KnowledgeEvidence> retrieve(Request request, AgentCallControl control);
        void recheck(Context context, List<KnowledgeEvidence> evidence);
    }
    public interface RunLedger {
        /** 先按持久化主体鉴权，再原子领取；重复 run 不得获得第二个执行许可。 */
        Lease claim(Request request, String requestHash);
        interface Lease extends AutoCloseable {
            /** 重新核对真实管理员身份、快照、入口围栏、取消与运行资格。 */
            void checkActive();
            void reserveModel(Stage stage);
            void recordModelUsage(Stage stage, Usage usage);
            /** 原子保留本次调用序号／预算；Gateway 仍须先提交派发意图再联网。 */
            void reserveTool(ToolIntent intent);
            void recordTool(int callNo, String invocationId, boolean succeeded, Dispatch dispatch);
            void complete(List<ToolSource> sources);
            /** 聊天实现将结果与助手消息在同一事务提交；工作流保留原完成机制。 */
            default void complete(Result result) { complete(result.toolSources()); }
            void fail(Code code, Dispatch dispatch);
            @Override void close();
        }
    }
}
