package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模型配置实体类
 * 对应数据库表 ai_model_config
 * 
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 模型配置实体类")
public class AiModelConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属中台租户，管理端共享资源为空 */
    @Schema(description = "所属中台租户")
    private Long tenantId;

    /** 配置名称 */
    @Schema(description = "配置名称")
    private String name;

    /** Polaris 稳定逻辑模型编码，V2 过渡期允许为空 */
    @Schema(description = "Polaris 稳定逻辑模型编码")
    private String modelCode;

    /** Provider Connection ID，V2 过渡期允许为空 */
    @Schema(description = "Provider Connection ID")
    private Long connectionId;

    /** 运行配置修订号 */
    @Schema(description = "运行配置修订号")
    private Long revision;

    /** Provider 侧实际模型名称 */
    @Schema(description = "模型名称")
    private String modelName;

    /** 稳定分类标签，仅用于展示/筛选，不参与能力路由 */
    @Schema(description = "稳定分类标签")
    private String modelType;

    /** 部门ID */
    @Schema(description = "部门ID")
    private Long deptId;

    /** 模型备注（管理员可见，不参与路由） */
    @Schema(description = "模型备注")
    private String description;

    /** 状态 (1正常 0禁用) */
    @Schema(description = "状态 (1正常 0禁用)")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

}
