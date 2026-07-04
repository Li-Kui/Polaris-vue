package com.polaris.ai.domain;

import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 知识库实体
 * 对应数据库表 ai_knowledge_base
 * 
 * @author polaris
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 知识库实体")
public class AiKnowledgeBase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 知识库主键 ID */
    @Schema(description = "知识库主键 ID")
    private Long id;

    /** 知识库名称 */
    @Schema(description = "知识库名称")
    private String name;

    /** 知识库描述 */
    @Schema(description = "知识库描述")
    private String description;

    /** 部门 ID */
    @Schema(description = "部门 ID")
    private Long deptId;
}
