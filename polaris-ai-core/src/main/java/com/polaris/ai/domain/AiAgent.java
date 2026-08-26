package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI智能体定义实体类
 * 对应数据库表 ai_agent
 *
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI智能体定义实体类")
public class AiAgent extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属中台租户，管理端共享资源为空 */
    @Schema(description = "所属中台租户")
    private Long tenantId;

    /** 智能体唯一编码 */
    @Schema(description = "智能体唯一编码")
    private String agentCode;

    /** 智能体名称 */
    @Schema(description = "智能体名称")
    private String agentName;

    /** 底座大模型名称 */
    @Schema(description = "底座大模型名称")
    private String modelName;

    /** 底座大模型配置ID */
    @Schema(description = "底座大模型配置ID")
    private Long modelConfigId;

    /** 系统角色提示词 */
    @Schema(description = "系统角色提示词")
    private String systemPrompt;

    /** 随机温度 */
    @Schema(description = "随机温度")
    private Double temperature;

    /** 绑定工具类列表，英文逗号分隔 */
    @Schema(description = "绑定工具类列表，英文逗号分隔")
    private String tools;

    /** 状态(1启用 0禁用) */
    @Schema(description = "状态(1启用 0禁用)")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
