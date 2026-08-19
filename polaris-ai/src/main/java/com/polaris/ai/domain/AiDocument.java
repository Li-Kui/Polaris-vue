package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档明细实体
 * 对应数据库表 ai_document
 * 
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库文档明细实体")
public class AiDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文档主键 ID */
    @Schema(description = "文档主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的知识库 ID */
    @Schema(description = "关联的知识库 ID")
    private Long knowledgeBaseId;

    /** 文档名称 */
    @Schema(description = "文档名称")
    private String name;

    /** 文件存储路径/OSS链接 */
    @Schema(description = "文件存储路径/OSS链接")
    private String fileUrl;

    /**
     * 解析状态
     * 0 —— 待解析
     * 1 —— 解析中
     * 2 —— 已解析
     * 3 —— 解析失败
     */
    @Schema(description = "解析状态：0-待解析，1-解析中，2-已解析，3-解析失败")
    private String status;

    /** 总字数 */
    @Schema(description = "总字数")
    private Integer wordCount;

    /**
     * 安全检测状态
     * WAIT_SCAN —— 等待机器检测
     * SCANNING —— 机器检测中
     * SAFE —— 检测通过
     * QUARANTINED —— 已隔离
     * SCAN_FAILED —— 检测失败
     * AUTO_DELETED —— 已自动清理
     */
    @Schema(description = "安全检测状态：WAIT_SCAN, SCANNING, SAFE, QUARANTINED, SCAN_FAILED, AUTO_DELETED")
    private String moderationStatus;

    /** 安全检测事件ID */
    @Schema(description = "安全检测事件ID")
    private Long moderationEventId;

    /** 检测时生效的词库版本号 */
    @Schema(description = "检测时生效的词库版本号")
    private Long moderationVersion;

    /** 隔离区物理存储路径 */
    @Schema(description = "隔离区物理存储路径")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String quarantinePath;

    /** 隔离到期时间 */
    @Schema(description = "隔离到期时间")
    private java.util.Date quarantineExpireTime;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}

