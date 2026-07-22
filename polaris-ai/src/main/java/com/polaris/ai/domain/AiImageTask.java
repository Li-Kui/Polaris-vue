package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * AI 图像生成任务实体类
 * 对应数据库表 ai_image_task
 * 
 * @author polaris
 */
@Data
@TableName("ai_image_task")
@Schema(description = "AI 图像生成任务实体类")
public class AiImageTask {

    /** 任务ID (UUID) */
    @Schema(description = "任务ID (UUID)")
    @TableId
    private String taskId;

    /** 绘图提示词 */
    @Schema(description = "绘图提示词")
    private String prompt;

    /** 任务状态（0生成中 1生成成功 2生成失败） */
    @Schema(description = "任务状态（0生成中 1生成成功 2生成失败）")
    private String status;

    /** 生成的图片OSS/本地存储路径 */
    @Schema(description = "生成的图片OSS/本地存储路径")
    private String imageUrl;

    /** 失败原因描述 */
    @Schema(description = "失败原因描述")
    private String errorMsg;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private Date createTime;

    /** 生成能力(10种之一)，如 text_to_image / inpainting / object_removal */
    @Schema(description = "生成能力")
    private String generationMode;

    /** 实际使用的模型配置ID */
    @Schema(description = "模型配置ID")
    private Long modelConfigId;

    /** 关联会话ID，直连绘图时为空 */
    @Schema(description = "会话ID")
    private Long conversationId;

    /** 实际使用的厂商 */
    @Schema(description = "厂商")
    private String provider;

    /** 输入图URL列表JSON(/profile/...) */
    @Schema(description = "输入图URL列表JSON")
    private String sourceImages;

    /** 遮罩图URL(/profile/...) */
    @Schema(description = "遮罩图URL")
    private String maskImage;

    /** 参数JSON:{size,n,negativePrompt,...} */
    @Schema(description = "生成参数JSON")
    private String imageParams;

    /** 耗时(ms) */
    @Schema(description = "耗时(ms)")
    private Long costTime;

    /** 创建者(数据权限) */
    @Schema(description = "创建者")
    private String createBy;

    /** 部门ID(数据权限) */
    @Schema(description = "部门ID")
    private Long deptId;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private Date updateTime;
}
