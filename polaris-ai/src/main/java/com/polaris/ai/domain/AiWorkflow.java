package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI智能体工作流实体类
 * 对应数据库表 ai_workflow
 *
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI智能体工作流实体类")
public class AiWorkflow extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 工作流唯一编码 */
    @Schema(description = "工作流唯一编码")
    private String workflowCode;

    /** 工作流名称 */
    @Schema(description = "工作流名称")
    private String workflowName;

    /** 描述 */
    @Schema(description = "描述")
    private String description;

    /** 流程节点编排JSON (如: ["intent_route", "sys_user_analyst"]) */
    @Deprecated(since = "3.10.0", forRemoval = true)
    @Schema(description = "流程节点编排JSON（已废弃，请使用 graphJson）")
    private String nodes;

    /** 图拓扑描述JSON（统一格式，替代 nodes） */
    @Schema(description = "图拓扑描述JSON")
    private String graphJson;

    /** 状态(1启用 0禁用) */
    @Schema(description = "状态(1启用 0禁用)")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
