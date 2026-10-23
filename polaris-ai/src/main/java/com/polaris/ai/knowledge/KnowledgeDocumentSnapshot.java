package com.polaris.ai.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("ai_document_index_snapshot")
public class KnowledgeDocumentSnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private String snapshotKey;
    private Long tenantId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Long indexVersion;
    private String collectionHash;
    private String snapshotState;
    private String fileHash;
    private String contentHash;
    private String payloadHash;
    private String parserVersion;
    private String splitterVersion;
    private Integer parsedChars;
    private Integer chunkCount;
    private String warningsJson;
    private String errorCode;
    private Date createTime;
    private String payloadCleanupState = "NONE";
    private Integer payloadCleanupAttempts = 0;
    private String payloadCleanupError;
    private Date payloadCleanupAfter;
}
