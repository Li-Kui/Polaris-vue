package com.polaris.ai.safety.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.safety.dto.PrivateAttachmentUploadResult;
import com.polaris.ai.safety.dto.ResolvedAttachment;
import com.polaris.ai.safety.mapper.PrivateAttachmentMapper;
import com.polaris.ai.safety.model.PrivateAttachment;
import com.polaris.ai.safety.service.IPrivateAttachmentService;
import com.polaris.ai.safety.service.PrivateStoragePaths;
import com.polaris.ai.safety.service.SafeFileOperations;
import com.polaris.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * AI 私有附件服务层实现类
 *
 * @author polaris
 */
@Service
public class PrivateAttachmentServiceImpl extends ServiceImpl<PrivateAttachmentMapper, PrivateAttachment>
        implements IPrivateAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(PrivateAttachmentServiceImpl.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final int MAX_TOKEN_COUNT = 5;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "xlsx", "xls", "txt", "md", "json", "xml", "csv",
            "html", "java", "py", "js", "ts", "png", "jpg", "jpeg", "gif", "webp", "bmp"
    );

    @Autowired(required = false)
    private PrivateStoragePaths storagePaths;

    @Autowired(required = false)
    private PrivateAttachmentMapper attachmentMapper;

    @Autowired(required = false)
    private SafeFileOperations safeFileOps;

    public void setStoragePaths(PrivateStoragePaths storagePaths) {
        this.storagePaths = storagePaths;
    }

    public void setAttachmentMapper(PrivateAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    public void setSafeFileOps(SafeFileOperations safeFileOps) {
        this.safeFileOps = safeFileOps;
    }

    @Override
    public PrivateAttachmentUploadResult stage(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传附件不能为空");
        }
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("附件大小超过限制（最大10MB）");
        }

        String originalFilename = file.getOriginalFilename();
        validateFilename(originalFilename);

        String ext = getExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ServiceException("不支持的附件类型: ." + ext);
        }

        if (storagePaths != null) {
            storagePaths.ensureDirectories();
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        String storedFileName = token + "_" + sanitizeFilename(originalFilename);
        Path targetPath = storagePaths != null
                ? storagePaths.stagedAttachmentsDir().resolve(storedFileName).normalize()
                : Path.of("/tmp", storedFileName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("保存私有附件文件失败, file={}", originalFilename, e);
            throw new ServiceException("附件保存失败");
        }

        Date expireTime = new Date(System.currentTimeMillis() + 2 * 3600 * 1000L); // 2 小时后过期
        PrivateAttachment record = new PrivateAttachment();
        record.setAttachmentToken(token);
        record.setUserId(userId);
        record.setOriginalName(originalFilename);
        record.setStoragePath(targetPath.toAbsolutePath().toString());
        record.setStatus("STAGED");
        record.setExpireTime(expireTime);
        record.setCreateTime(new Date());

        if (attachmentMapper != null) {
            attachmentMapper.insert(record);
        }

        return new PrivateAttachmentUploadResult(
                token,
                originalFilename,
                file.getSize(),
                file.getContentType()
        );
    }

    @Override
    public ResolvedAttachment resolveForDownload(String token, Long userId) {
        if (token == null || token.isBlank()) {
            throw new ServiceException("附件Token不能为空");
        }
        if (userId == null) {
            throw new ServiceException("未授权访问附件");
        }

        PrivateAttachment attachment = null;
        if (attachmentMapper != null) {
            attachment = attachmentMapper.selectOne(
                    new LambdaQueryWrapper<PrivateAttachment>()
                            .eq(PrivateAttachment::getAttachmentToken, token)
                            .eq(PrivateAttachment::getUserId, userId)
                            .in(PrivateAttachment::getStatus, List.of("STAGED", "CLAIMED"))
            );
        }

        if (attachment == null) {
            throw new ServiceException("附件不存在或无权访问");
        }

        Path path = Path.of(attachment.getStoragePath());
        if (!Files.exists(path)) {
            throw new ServiceException("附件文件已不存在");
        }

        String mediaType = getMediaTypeFromFilename(attachment.getOriginalName());
        return new ResolvedAttachment(
                attachment.getAttachmentToken(),
                path,
                attachment.getOriginalName(),
                mediaType
        );
    }

    @Override
    public List<ResolvedAttachment> resolveOwned(List<String> tokens, Long userId) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.size() > MAX_TOKEN_COUNT) {
            throw new ServiceException("单次输入关联附件数量不能超过 " + MAX_TOKEN_COUNT + " 个");
        }
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }

        List<PrivateAttachment> list = List.of();
        if (attachmentMapper != null) {
            list = attachmentMapper.selectList(
                    new LambdaQueryWrapper<PrivateAttachment>()
                            .in(PrivateAttachment::getAttachmentToken, tokens)
                            .eq(PrivateAttachment::getUserId, userId)
                            .eq(PrivateAttachment::getStatus, "STAGED")
                            .gt(PrivateAttachment::getExpireTime, new Date())
            );
        }

        Map<String, PrivateAttachment> map = new HashMap<>();
        if (list != null) {
            for (PrivateAttachment a : list) {
                if (Objects.equals(a.getUserId(), userId) &&
                        "STAGED".equals(a.getStatus()) &&
                        (a.getExpireTime() == null || a.getExpireTime().after(new Date()))) {
                    map.put(a.getAttachmentToken(), a);
                }
            }
        }

        List<ResolvedAttachment> results = new ArrayList<>();
        for (String token : tokens) {
            PrivateAttachment att = map.get(token);
            if (att == null) {
                throw new ServiceException("附件无效或已过期: " + token);
            }
            Path path = Path.of(att.getStoragePath());
            if (!Files.exists(path)) {
                throw new ServiceException("附件文件不存在: " + att.getOriginalName());
            }
            results.add(new ResolvedAttachment(
                    att.getAttachmentToken(),
                    path,
                    att.getOriginalName(),
                    getMediaTypeFromFilename(att.getOriginalName())
            ));
        }
        return results;
    }

    @Override
    public void deleteStaged(String token, Long userId) {
        if (token == null || token.isBlank() || userId == null || attachmentMapper == null) {
            return;
        }
        PrivateAttachment att = attachmentMapper.selectOne(
                new LambdaQueryWrapper<PrivateAttachment>()
                        .eq(PrivateAttachment::getAttachmentToken, token)
                        .eq(PrivateAttachment::getUserId, userId)
                        .eq(PrivateAttachment::getStatus, "STAGED")
        );
        if (att != null) {
            if (safeFileOps != null) {
                try {
                    safeFileOps.delete(Path.of(att.getStoragePath()));
                } catch (Exception e) {
                    log.warn("删除暂存附件物理文件失败: token={}", token, e);
                }
            } else {
                try {
                    Files.deleteIfExists(Path.of(att.getStoragePath()));
                } catch (IOException ignored) {
                }
            }
            attachmentMapper.deleteById(att.getId());
        }
    }

    @Override
    public void markClaimed(List<String> tokens, Long userId) {
        if (tokens == null || tokens.isEmpty() || userId == null || attachmentMapper == null) {
            return;
        }
        List<PrivateAttachment> list = attachmentMapper.selectList(
                new LambdaQueryWrapper<PrivateAttachment>()
                        .in(PrivateAttachment::getAttachmentToken, tokens)
                        .eq(PrivateAttachment::getUserId, userId)
        );
        if (list != null) {
            for (PrivateAttachment att : list) {
                if (Objects.equals(att.getUserId(), userId)) {
                    att.setStatus("CLAIMED");
                    att.setUpdateTime(new Date());
                    attachmentMapper.updateById(att);
                }
            }
        }
    }

    @Override
    public void markRejected(List<String> tokens, Long userId) {
        if (tokens == null || tokens.isEmpty() || userId == null || attachmentMapper == null) {
            return;
        }
        List<PrivateAttachment> list = attachmentMapper.selectList(
                new LambdaQueryWrapper<PrivateAttachment>()
                        .in(PrivateAttachment::getAttachmentToken, tokens)
                        .eq(PrivateAttachment::getUserId, userId)
        );
        if (list != null) {
            for (PrivateAttachment att : list) {
                if (Objects.equals(att.getUserId(), userId)) {
                    att.setStatus("REJECTED");
                    att.setUpdateTime(new Date());
                    attachmentMapper.updateById(att);
                }
            }
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ServiceException("文件名不能为空");
        }
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..") || filename.contains(",")) {
            throw new ServiceException("文件名包含非法路径字符");
        }
        if (!filename.contains(".") || filename.endsWith(".")) {
            throw new ServiceException("文件名缺少扩展名");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1);
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getMediaTypeFromFilename(String filename) {
        String ext = getExtension(filename).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "csv" -> "text/csv";
            case "html" -> "text/html";
            default -> "application/octet-stream";
        };
    }
}
