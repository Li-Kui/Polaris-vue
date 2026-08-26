package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属中台租户，管理端共享资源为空 */
    @Schema(description = "所属中台租户")
    private Long tenantId;

    /** 知识库名称 */
    @Schema(description = "知识库名称")
    private String name;

    /** 知识库描述 */
    @Schema(description = "知识库描述")
    private String description;

    /** 当前知识库绑定的向量模型配置 ID */
    @Schema(description = "绑定的向量模型配置 ID")
    private Long embeddingModelId;

    /** 当前生效的向量 collection */
    @Schema(description = "当前生效的向量 collection")
    private String vectorCollection;

    /** 递归切片最大字符数 */
    @Schema(description = "切片最大字符数")
    private Integer chunkSize;

    /** 相邻切片重叠字符数 */
    @Schema(description = "切片重叠字符数")
    private Integer chunkOverlap;

    /** 切片算法，目前支持 RECURSIVE */
    @Schema(description = "切片算法")
    private String splitterType;

    /** RAG 最大召回数量 */
    @Schema(description = "RAG 最大召回数量")
    private Integer retrievalTopK;

    /** RAG 最低相似度 */
    @Schema(description = "RAG 最低相似度")
    private Double retrievalMinScore;

    /** 当前索引版本 */
    @Schema(description = "当前索引版本")
    private Long indexVersion;

    /** 当前索引配置签名 */
    @Schema(description = "当前索引配置签名")
    private String indexSignature;

    /** 索引状态：EMPTY/BUILDING/READY/STALE/FAILED */
    @Schema(description = "索引状态")
    private String indexStatus;

    /** 最近一次索引失败原因 */
    @Schema(description = "最近一次索引失败原因")
    private String indexError;

    /** 部门 ID */
    @Schema(description = "部门 ID")
    private Long deptId;

    /** 删除标志（0代表存在 2代表删除） */
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
