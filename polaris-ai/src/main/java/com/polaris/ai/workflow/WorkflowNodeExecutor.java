package com.polaris.ai.workflow;

/**
 * 纯 Java 编写的工作流节点执行器接口
 * 允许用户实现自定义业务节点（如数据清洗、格式转换、通知发送等），并在工作流中直接挂载
 *
 * @author polaris
 */
public interface WorkflowNodeExecutor {

    /**
     * 执行业务节点逻辑
     *
     * @param context 工作流上下文
     */
    void execute(WorkflowContext context);

    /**
     * 获取节点唯一编码
     *
     * @return 节点编码
     */
    String getNodeCode();
}
