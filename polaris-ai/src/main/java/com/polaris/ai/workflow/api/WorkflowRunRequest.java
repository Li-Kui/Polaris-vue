package com.polaris.ai.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 启动工作流请求；执行标识只能由服务端生成。 */
@Data
public class WorkflowRunRequest {

    @NotBlank(message = "工作流编码不能为空")
    @Size(max = 50, message = "工作流编码长度不能超过50")
    private String workflowCode;

    @NotBlank(message = "消息不能为空")
    @Size(max = 20000, message = "消息长度不能超过20000")
    private String message;

    @Size(max = 2000, message = "附件引用长度不能超过2000")
    private String fileUrl;

    /** 私有附件 Token 列表 */
    private java.util.List<String> attachmentTokens;

    private Long conversationId;

    /** 编排管理页试运行，不写入真实会话。 */
    private boolean testRun;
}
