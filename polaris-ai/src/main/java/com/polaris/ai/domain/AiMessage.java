package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * AI 对话消息实体
 * 对应数据库表 ai_message
 * 每条记录代表一次对话中的单条消息（用户发送或 AI 回复）
 * 
 * @author polaris
 */
@Data
@Schema(description = "AI 对话消息实体")
public class AiMessage
{
    /** 消息主键 ID */
    @Schema(description = "消息主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID，关联 ai_conversation 表 */
    @Schema(description = "所属会话 ID，关联 ai_conversation 表")
    private Long conversationId;

    /** 关联的工作流执行 ID，用于精确更新流式回复。 */
    @Schema(description = "关联的工作流执行 ID")
    private String workflowExecutionId;

    /**
     * 消息角色
     * user      —— 用户发送的消息
     * assistant —— AI 回复的消息
     */
    @Schema(description = "消息角色：user - 用户发送的消息，assistant - AI 回复的消息")
    private String role;

    /** 消息正文内容（支持 Markdown 格式） */
    @Schema(description = "消息正文内容（支持 Markdown 格式）")
    private String content;

    /** 思考过程内容 */
    @Schema(description = "思考过程内容")
    private String reasoningContent;

    /** 本条消息消耗的 Token 数量（AI 回复时记录，用户消息为 0） */
    @Schema(description = "本条消息消耗的 Token 数量（AI 回复时记录，用户消息为 0）")
    private Integer tokens;

    /** 附件文件路径/链接 */
    @Schema(description = "附件文件路径/链接")
    private String fileUrl;

    /** 附件原始名称 */
    @Schema(description = "附件原始名称")
    private String fileName;

    /** 附件解析出的内容 */
    @Schema(description = "附件解析出的内容")
    private String fileContent;

    /** 消息创建时间 */
    @Schema(description = "消息创建时间")
    private Date createTime;
}
