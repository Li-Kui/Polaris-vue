package com.polaris.ai.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.safety.mapper.ModerationCandidateMapper;
import com.polaris.ai.safety.mapper.ModerationEventMapper;
import com.polaris.ai.safety.mapper.PrivateAttachmentMapper;
import com.polaris.ai.safety.model.ModerationCandidate;
import com.polaris.ai.safety.model.ModerationEvent;
import com.polaris.ai.safety.model.PrivateAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Component("moderationCleanupTask")
public class ModerationCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(ModerationCleanupTask.class);

    @Autowired
    private AiDocumentMapper documentMapper;

    @Autowired
    private PrivateAttachmentMapper attachmentMapper;

    @Autowired
    private ModerationEventMapper eventMapper;

    @Autowired
    private ModerationCandidateMapper candidateMapper;

    @Autowired
    private SafeFileOperations safeFileOps;

    public ModerationCleanupTask() {}

    public ModerationCleanupTask(AiDocumentMapper documentMapper,
                                 PrivateAttachmentMapper attachmentMapper,
                                 ModerationEventMapper eventMapper,
                                 ModerationCandidateMapper candidateMapper,
                                 SafeFileOperations safeFileOps) {
        this.documentMapper = documentMapper;
        this.attachmentMapper = attachmentMapper;
        this.eventMapper = eventMapper;
        this.candidateMapper = candidateMapper;
        this.safeFileOps = safeFileOps;
    }

    public void setDocumentMapper(AiDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public void setAttachmentMapper(PrivateAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public void setEventMapper(ModerationEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    public void setCandidateMapper(ModerationCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    public void setSafeFileOps(SafeFileOperations safeFileOps) {
        this.safeFileOps = safeFileOps;
    }

    public void run() {
        log.info("开始执行 AI 敏感内容机器安全检测自动清理任务...");
        int qCount = cleanupQuarantineBatch(500);
        int aCount = cleanupPrivateAttachmentsBatch(500);
        int eCount = cleanupModerationEventsBatch(500);
        int cCount = cleanupCandidatesBatch(500);
        log.info("AI 敏感内容清理任务完成: 隔离文件={}, 私有附件={}, 审计事件={}, 候选词={}",
                qCount, aCount, eCount, cCount);
    }

    public int cleanupQuarantineBatch(int limit) {
        if (documentMapper == null) return 0;
        List<AiDocument> list = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getModerationStatus, "QUARANTINED")
                .le(AiDocument::getQuarantineExpireTime, new Date())
                .last("LIMIT " + limit));

        int cleaned = 0;
        for (AiDocument doc : list) {
            String pathStr = doc.getQuarantinePath();
            boolean fileDeleted = true;
            if (pathStr != null && !pathStr.isBlank()) {
                try {
                    if (safeFileOps != null) {
                        safeFileOps.delete(Path.of(pathStr));
                    }
                } catch (Exception e) {
                    fileDeleted = false;
                    log.warn("删除到期隔离物理文件失败: docId={}, path={}", doc.getId(), pathStr, e);
                }
            }
            if (fileDeleted) {
                doc.setModerationStatus("AUTO_DELETED");
                doc.setQuarantinePath(null);
                documentMapper.updateById(doc);
                cleaned++;
            }
        }
        return cleaned;
    }

    public int cleanupPrivateAttachmentsBatch(int limit) {
        if (attachmentMapper == null) return 0;
        List<PrivateAttachment> list = attachmentMapper.selectList(new LambdaQueryWrapper<PrivateAttachment>()
                .in(PrivateAttachment::getStatus, List.of("WAIT_SCAN", "REJECTED"))
                .le(PrivateAttachment::getExpireTime, new Date())
                .last("LIMIT " + limit));

        int cleaned = 0;
        for (PrivateAttachment att : list) {
            String pathStr = att.getStoragePath();
            boolean fileDeleted = true;
            if (pathStr != null && !pathStr.isBlank()) {
                try {
                    if (safeFileOps != null) {
                        safeFileOps.delete(Path.of(pathStr));
                    }
                } catch (Exception e) {
                    fileDeleted = false;
                    log.warn("删除到期私有附件物理文件失败: attId={}, path={}", att.getId(), pathStr, e);
                }
            }
            if (fileDeleted) {
                att.setStatus("EXPIRED");
                attachmentMapper.updateById(att);
                cleaned++;
            }
        }
        return cleaned;
    }

    public int cleanupModerationEventsBatch(int limit) {
        if (eventMapper == null) return 0;
        return eventMapper.delete(new LambdaQueryWrapper<ModerationEvent>()
                .le(ModerationEvent::getExpireTime, new Date())
                .last("LIMIT " + limit));
    }

    public int cleanupCandidatesBatch(int limit) {
        if (candidateMapper == null) return 0;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30); // 清理30天前的REJECTED候选词
        Date threshold = cal.getTime();

        return candidateMapper.delete(new LambdaQueryWrapper<ModerationCandidate>()
                .eq(ModerationCandidate::getStatus, "REJECTED")
                .le(ModerationCandidate::getUpdateTime, threshold)
                .last("LIMIT " + limit));
    }
}
