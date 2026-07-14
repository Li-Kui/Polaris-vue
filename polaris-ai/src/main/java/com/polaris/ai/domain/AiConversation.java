package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 对话会话实体
 * 对应数据库表 ai_conversation
 * 每条记录代表一个独立的对话会话，包含该会话的基本信息和状态
 * 继承 BaseEntity 获得 createBy、createTime、updateTime 等公共字段
 *
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 对话会话实体")
public class AiConversation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会话主键 ID */
    @Schema(description = "会话主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话标题（首条消息自动截取前15字，也可手动重命名） */
    @Schema(description = "会话标题（首条消息自动截取前15字，也可手动重命名）")
    private String title;

    /** 创建该会话的用户 ID，关联 sys_user 表 */
    @Schema(description = "创建该会话的用户 ID，关联 sys_user 表")
    private Long userId;

    /**
     * 本次会话使用的 AI 模型名称
     * 例如：qwen-plus / qwen-turbo / deepseek-chat / gpt-4o-mini
     */
    @Schema(description = "本次会话使用的 AI 模型名称，例如：qwen-plus / qwen-turbo / deepseek-chat / gpt-4o-mini")
    private String model;

    /** 本次会话使用的大模型配置ID */
    @Schema(description = "本次会话使用的大模型配置ID")
    private Long modelConfigId;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    private String delFlag;

    /** 关联的知识库 ID */
    @Schema(description = "关联的知识库 ID")
    private Long knowledgeBaseId;
}
