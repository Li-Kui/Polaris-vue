package com.polaris.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 自动识别并挂载的文档元数据 DTO
 */
@Schema(description = "自动识别挂载文档 DTO")
public class RecognizedDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文档 ID")
    private Long docId;

    @Schema(description = "文档名称")
    private String docName;

    @Schema(description = "知识库 ID")
    private Long knowledgeBaseId;

    @Schema(description = "匹配类型: MENTION(显式提及/语法引用), AUTO_SEMANTIC(隐式语义召回), SESSION_UPLOAD(临时文件)")
    private String matchType;

    @Schema(description = "匹配置信度/相似度分数 (0.0 ~ 1.0)")
    private Double confidence;

    @Schema(description = "匹配到的核心切片摘要上下文")
    private String matchedContent;

    public RecognizedDoc() {
    }

    public RecognizedDoc(Long docId, String docName, Long knowledgeBaseId, String matchType, Double confidence, String matchedContent) {
        this.docId = docId;
        this.docName = docName;
        this.knowledgeBaseId = knowledgeBaseId;
        this.matchType = matchType;
        this.confidence = confidence;
        this.matchedContent = matchedContent;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getMatchedContent() {
        return matchedContent;
    }

    public void setMatchedContent(String matchedContent) {
        this.matchedContent = matchedContent;
    }
}
