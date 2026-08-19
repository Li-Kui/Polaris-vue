package com.polaris.ai.safety.service.impl;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IQuarantineService;
import com.polaris.ai.safety.service.PrivateStoragePaths;
import com.polaris.ai.safety.service.SafeFileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;

/**
 * AI 文档安全隔离服务层实现类
 *
 * @author polaris
 */
@Service
public class QuarantineServiceImpl implements IQuarantineService {

    private static final Logger log = LoggerFactory.getLogger(QuarantineServiceImpl.class);

    @Autowired(required = false)
    private PrivateStoragePaths storagePaths;

    @Autowired(required = false)
    private SafeFileOperations safeFileOps;

    @Autowired(required = false)
    private AiDocumentMapper documentMapper;

    @Autowired(required = false)
    private ModerationProperties properties;

    public void setStoragePaths(PrivateStoragePaths storagePaths) {
        this.storagePaths = storagePaths;
    }

    public void setSafeFileOps(SafeFileOperations safeFileOps) {
        this.safeFileOps = safeFileOps;
    }

    public void setDocumentMapper(AiDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public void setProperties(ModerationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void quarantine(AiDocument doc, ModerationResult moderation, Path sourceFile) {
        if (doc == null) {
            return;
        }
        if (storagePaths != null) {
            storagePaths.ensureDirectories();
        }

        String ext = getExtension(doc.getName());
        String quarantineFileName = doc.getId() + "_" + UUID.randomUUID().toString().replace("-", "")
                + (ext.isBlank() ? "" : "." + ext);
        Path quarantinePath = storagePaths != null
                ? storagePaths.quarantineDir().resolve(quarantineFileName).normalize()
                : Path.of("/tmp", quarantineFileName);

        if (sourceFile != null && Files.exists(sourceFile) && safeFileOps != null) {
            try {
                safeFileOps.move(sourceFile, quarantinePath);
                doc.setQuarantinePath(quarantinePath.toAbsolutePath().toString());
            } catch (Exception e) {
                log.error("移动文档至隔离区失败, docId={}, file={}", doc.getId(), sourceFile, e);
            }
        }

        int quarantineDays = 7;
        if (properties != null && properties.getScenes() != null && properties.getScenes().containsKey(ModerationScene.KNOWLEDGE)) {
            quarantineDays = properties.getScenes().get(ModerationScene.KNOWLEDGE).getQuarantineDays();
        }
        Date expireTime = new Date(System.currentTimeMillis() + quarantineDays * 86400000L);

        doc.setModerationStatus("QUARANTINED");
        doc.setStatus("3"); // 状态置为失败/不可索引
        doc.setModerationVersion(moderation != null ? moderation.dictionaryVersion() : null);
        doc.setQuarantineExpireTime(expireTime);

        if (documentMapper != null) {
            documentMapper.updateById(doc);
        }

        log.warn("文档已被安全隔离: docId={}, name={}, expireTime={}", doc.getId(), doc.getName(), expireTime);
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1);
    }
}
