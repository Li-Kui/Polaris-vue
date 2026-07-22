package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.polaris.common.annotation.Sensitive;
import com.polaris.common.core.domain.BaseEntity;
import com.polaris.common.enums.DesensitizedType;
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

    /** 配置名称 */
    @Schema(description = "配置名称")
    private String name;

    /** 提供商 (dashscope / openai / deepseek / ollama) */
    @Schema(description = "提供商 (dashscope / openai / deepseek / ollama)")
    private String provider;

    /** 模型名称 */
    @Schema(description = "模型名称")
    private String modelName;

    /** API Key */
    @Schema(description = "API Key")
    @Sensitive(desensitizedType = DesensitizedType.PASSWORD)
    private String apiKey;

    /** API Base URL */
    @Schema(description = "API Base URL")
    private String baseUrl;

    /** 最大Token数 */
    @Schema(description = "最大Token数")
    private Integer maxTokens;

    /** 随机温度 */
    @Schema(description = "随机温度")
    private Double temperature;

    /** 最大历史消息数 */
    @Schema(description = "最大历史消息数")
    private Integer maxHistoryMessages;

    /** 模型专属系统提示词 */
    @Schema(description = "模型专属系统提示词")
    private String systemPrompt;

    /** 是否默认聊天模型 (1是 0否) */
    @Schema(description = "是否默认聊天模型 (1是 0否)")
    private String isDefault;

    /** 模型用途类型 (CHAT / EMBEDDING / IMAGE) */
    @Schema(description = "模型用途类型 (CHAT / EMBEDDING / IMAGE)")
    private String modelType;

    /** 当前模型启用的工具集白名单 */
    @Schema(description = "当前模型启用的工具集白名单")
    private String enabledTools;

    /** 是否开启思考模式 (1是 0否) */
    @Schema(description = "是否开启思考模式 (1是 0否)")
    private String enableThinking;

    /** 思考强度 (low/medium/high/max) */
    @Schema(description = "思考强度 (low/medium/high/max)")
    private String reasoningEffort;

    /** 部门ID */
    @Schema(description = "部门ID")
    private Long deptId;

    /** 是否启用联网搜索 (1是 0否) */
    @Schema(description = "是否启用联网搜索 (1是 0否)")
    private String enableSearch;

    /** 联网搜索 API Key */
    @Schema(description = "联网搜索 API Key")
    @Sensitive(desensitizedType = DesensitizedType.PASSWORD)
    private String searchKey;

    /** 图像能力清单JSON数组（仅IMAGE类型有效），如 ["text_to_image","inpainting"] */
    @Schema(description = "图像能力清单JSON数组")
    private String imageCapabilities;

    /** 模型质量标签JSON数组，用于推荐排序，如 ["text_rendering","photorealistic"] */
    @Schema(description = "模型质量标签JSON数组")
    private String modelFeatures;

    /** 默认出图尺寸，如 1024x1024 */
    @Schema(description = "默认出图尺寸")
    private String defaultImageSize;

    /** 模型备注（管理员可见，不参与路由） */
    @Schema(description = "模型备注")
    private String modelDescription;

    /** 状态 (1正常 0禁用) */
    @Schema(description = "状态 (1正常 0禁用)")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}

