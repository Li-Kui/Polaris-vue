package com.polaris.ai.domain;

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
}
