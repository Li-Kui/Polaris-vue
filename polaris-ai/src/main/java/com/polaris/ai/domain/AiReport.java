package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 分析报告实体类
 * 对应数据库表 ai_report
 *
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_report")
@Schema(description = "AI分析报告实体类")
public class AiReport extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 报告ID
     */
    @Schema(description = "报告ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 报告编号
     */
    @Schema(description = "报告编号")
    private String reportCode;

    /**
     * 报告标题
     */
    @Schema(description = "报告标题")
    private String reportTitle;

    /**
     * 关联会话ID
     */
    @Schema(description = "关联会话ID")
    private Long conversationId;

    /**
     * 关联智能体编码
     */
    @Schema(description = "关联智能体编码")
    private String agentCode;

    /**
     * 报告 Markdown 正文
     */
    @Schema(description = "报告 Markdown 正文")
    private String reportContent;

    /**
     * 统计指标 JSON
     */
    @Schema(description = "统计指标 JSON")
    private String reportStats;

    /**
     * 所属用户ID
     */
    @Schema(description = "所属用户ID")
    private Long userId;

    /**
     * 状态（0正常 1归档）
     */
    @Schema(description = "状态（0正常 1归档）")
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

}
