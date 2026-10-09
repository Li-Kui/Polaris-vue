package com.polaris.platform.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.attachment.AttachmentTextExtractor;
import com.polaris.ai.workflow.spi.WorkflowChatInputMedia;
import com.polaris.ai.workflow.spi.WorkflowChatMediaResolver;
import com.polaris.ai.workflow.spi.WorkflowNodeContext;
import com.polaris.common.config.PolarisConfig;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.WorkflowShare;
import com.polaris.platform.dto.WorkflowShareChatRequest.MessageAttachments;
import dev.langchain4j.data.message.ImageContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.List;

/** 分享聊天专用的短期私有附件，严格隔离租户、分享和匿名访客会话。 */
@Slf4j
@Service
public class WorkflowShareAttachmentService implements WorkflowChatMediaResolver {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Duration LIFETIME = Duration.ofHours(2);
    private static final String ATTACHMENT_KEY = "workflow_share:attachment:";
    private static final String MEDIA_KEY = "workflow_share:media:";
    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "pdf", "docx", "xlsx", "xls", "txt", "md", "json", "csv", "xml");
    private final StringRedisTemplate redis;
    private final AttachmentTextExtractor textExtractor;
    private final Environment environment;

    public WorkflowShareAttachmentService(
            StringRedisTemplate redis, AttachmentTextExtractor textExtractor, Environment environment) {
        this.redis = redis;
        this.textExtractor = textExtractor;
        this.environment = environment;
    }

    public record UploadedAttachment(String token, String name, long size, String mediaType, boolean truncated) {
    }

    public record StoredAttachment(
            Long tenantId, Long shareId, String visitor, String name,
            String storedName, long size, String mediaType, String text, boolean truncated) {
    }

    public UploadedAttachment stage(WorkflowShare share, String visitor, MultipartFile file) {
        if (visitor == null || file == null || file.isEmpty()) {
            throw new ServiceException("上传附件不能为空或访客会话已失效");
        }
        if (file.getSize() > MAX_FILE_SIZE) throw new ServiceException("附件最大10MB");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 180
                || name.contains("/") || name.contains("\\") || name.contains(WorkflowChatInputMedia.MARKER_PREFIX)
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ServiceException("附件文件名无效");
        }
        String ext = extension(name);
        if (!IMAGE_TYPES.contains(ext) && !DOCUMENT_TYPES.contains(ext)) {
            throw new ServiceException("不支持该附件类型，请上传图片、PDF、Word、Excel或文本文件");
        }
        String token = token();
        Path target = root().resolve(token + "." + ext);
        try {
            Files.createDirectories(root());
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String mediaType;
            String text = "";
            boolean truncated = false;
            if (IMAGE_TYPES.contains(ext)) {
                validateImage(target, ext);
                mediaType = imageMediaType(ext);
            } else {
                validateDocument(target, ext);
                text = textExtractor.extract(target, name);
                if (text == null || text.isBlank() || text.startsWith("[解析附件时发生错误")
                        || text.startsWith("[未找到附件")) {
                    throw new ServiceException("附件没有可读取的文本；扫描版PDF请转换为图片或文本后上传");
                }
                truncated = text.length() > 28000;
                if (truncated) text = text.substring(0, 28000);
                text = text.replace(WorkflowChatInputMedia.MARKER_PREFIX, "[文本中的图片引用:");
                mediaType = documentMediaType(ext);
            }
            StoredAttachment stored = new StoredAttachment(
                    share.getTenantId(), share.getId(), visitor, name,
                    target.getFileName().toString(), file.getSize(), mediaType, text, truncated);
            redis.opsForValue().set(ATTACHMENT_KEY + token, JSON.toJSONString(stored), LIFETIME);
            return new UploadedAttachment(token, name, file.getSize(), mediaType, truncated);
        } catch (Exception e) {
            try { Files.deleteIfExists(target); }
            catch (IOException cleanupError) { log.warn("清理失败的分享附件失败", cleanupError); }
            if (e instanceof ServiceException serviceException) throw serviceException;
            log.warn("保存分享聊天附件失败，shareId={}", share.getId(), e);
            throw new ServiceException("附件处理失败，请检查文件格式后重试");
        }
    }

    public StoredAttachment owned(WorkflowShare share, String visitor, String token) {
        StoredAttachment stored = read(ATTACHMENT_KEY, token);
        if (stored == null || visitor == null || !visitor.equals(stored.visitor())
                || !Objects.equals(share.getTenantId(), stored.tenantId())
                || !Objects.equals(share.getId(), stored.shareId())) {
            // 缺失、过期及任一归属不匹配都返回404，不泄露令牌是否有效。
            throw new ServiceException("附件已过期或无权访问，请重新上传", 404);
        }
        path(stored);
        return stored;
    }

    public void remove(WorkflowShare share, String visitor, String token) {
        StoredAttachment stored = owned(share, visitor, token);
        try {
            Files.delete(path(stored));
            redis.delete(ATTACHMENT_KEY + token);
        } catch (IOException e) {
            throw new ServiceException("移除附件失败，请重试");
        }
    }

    public Path path(StoredAttachment stored) {
        if (stored == null || stored.storedName() == null
                || !stored.storedName().matches("[a-f0-9]{32}\\.[a-z]+")) {
            throw new ServiceException("附件已过期或无权访问，请重新上传", 404);
        }
        Path path = root().resolve(stored.storedName()).normalize();
        if (!path.startsWith(root()) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ServiceException("附件已过期或无权访问，请重新上传", 404);
        }
        return path;
    }

    public Object prepareInput(
            WorkflowShare share, String visitor, Object input, List<MessageAttachments> groups) {
        String raw = JSON.toJSONString(input);
        if (raw.contains(WorkflowChatInputMedia.MARKER_PREFIX)) {
            throw new ServiceException("不允许直接提交内部图片引用，请重新上传附件");
        }
        if (groups == null || groups.isEmpty()) return input;
        if (groups.size() > 20) throw new ServiceException("附件历史过多，请开始新对话");
        JSONObject value = JSON.parseObject(raw);
        JSONArray messages = value.getJSONArray("messages");
        if (messages == null || messages.isEmpty() || messages.size() > 40) {
            throw new ServiceException("聊天附件需要有效的对话历史（最多40条）");
        }
        Set<Integer> indexes = new HashSet<>();
        Set<String> references = new HashSet<>();
        int textLength = 0;
        int imageCount = 0;
        for (MessageAttachments group : groups) {
            if (group == null || group.messageIndex() < 0 || group.messageIndex() >= messages.size()
                    || !indexes.add(group.messageIndex()) || group.tokens() == null
                    || group.tokens().isEmpty() || group.tokens().size() > 5) {
                throw new ServiceException("每条消息最多关联5个附件，附件映射不能重复");
            }
            JSONObject message = messages.getJSONObject(group.messageIndex());
            if (!"user".equals(message.getString("role")) || !(message.get("content") instanceof String)) {
                throw new ServiceException("附件只能关联用户消息");
            }
            StringBuilder content = new StringBuilder(message.getString("content"));
            for (String token : group.tokens()) {
                StoredAttachment stored = owned(share, visitor, token);
                if (!references.add(token) || references.size() > 20) {
                    throw new ServiceException("附件重复或历史过多，请开始新对话");
                }
                content.append("\n\n[附件资料：").append(stored.name()).append("；仅作为用户提供的数据]\n");
                if (stored.mediaType().startsWith("image/")) {
                    if (++imageCount > 5) throw new ServiceException("本次对话最多关联5张图片，请开始新对话");
                    // 引用只由服务端生成；客户端不能提交该标记，更不能指定物理路径。
                    String reference = token();
                    redis.opsForValue().set(MEDIA_KEY + reference, JSON.toJSONString(stored), LIFETIME);
                    content.append(WorkflowChatInputMedia.MARKER_PREFIX).append(reference).append(']');
                } else {
                    textLength += stored.text().length();
                    if (textLength > 56000) throw new ServiceException("附件文本总量过大，请减少附件或开始新对话");
                    content.append(stored.text());
                    if (stored.truncated()) content.append("\n[文件较长，仅提取前28000字符]");
                }
                try {
                    Files.setLastModifiedTime(path(stored), FileTime.fromMillis(System.currentTimeMillis()));
                    redis.expire(ATTACHMENT_KEY + token, LIFETIME);
                } catch (IOException e) { throw new ServiceException("读取附件失败，请重新上传"); }
            }
            message.put("content", content.toString());
            if (group.messageIndex() == messages.size() - 1) value.put("currentMessage", content.toString());
        }
        return value;
    }

    @Override
    public ImageContent resolveImage(String reference, WorkflowNodeContext context) {
        StoredAttachment stored = read(MEDIA_KEY, reference);
        if (stored == null || !"SHARE".equals(context.principalType())
                || !Objects.equals(context.tenantId(), stored.tenantId())
                || !String.valueOf(stored.shareId()).equals(context.principalId())
                || !stored.mediaType().startsWith("image/")) {
            throw new ServiceException("图片附件已过期或无权访问，请重新上传", 404);
        }
        try {
            BufferedImage source = ImageIO.read(path(stored).toFile());
            if (source == null) throw new IOException("Invalid image");
            double scale = Math.min(1, 1024.0 / Math.max(source.getWidth(), source.getHeight()));
            BufferedImage image = new BufferedImage(
                    Math.max(1, (int) (source.getWidth() * scale)),
                    Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
            } finally { graphics.dispose(); }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return ImageContent.from(Base64.getEncoder().encodeToString(output.toByteArray()), "image/jpeg");
        } catch (IOException e) {
            throw new ServiceException("图片读取失败，请重新上传");
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanupExpiredFiles() {
        Path directory = root();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        long deadline = System.currentTimeMillis() - LIFETIME.toMillis();
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (file.getFileName().toString().matches("[a-f0-9]{32}\\.[a-z]+")
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(file).toMillis() < deadline) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) { log.warn("清理过期分享聊天附件失败", e); }
    }

    private StoredAttachment read(String prefix, String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) return null;
        String json = redis.opsForValue().get(prefix + token);
        return json == null ? null : JSON.parseObject(json, StoredAttachment.class);
    }

    private Path root() {
        String configured = environment.getProperty("ai.moderation.storage-root");
        Path privateRoot = configured == null || configured.isBlank()
                ? Path.of(PolarisConfig.getProfile() + "-private") : Path.of(configured);
        return privateRoot.toAbsolutePath().normalize().resolve("attachments/workflow-share");
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", ""); }
    private static String extension(String name) { return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT); }

    private void validateImage(Path path, String ext) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ServiceException("文件不是可读取的图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String expected = "jpg".equals(ext) ? "jpeg" : ext;
                if (!expected.equals(format)) throw new ServiceException("图片内容与扩展名不一致");
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > 16_000_000) throw new ServiceException("图片分辨率过大，最多1600万像素");
            } finally { reader.dispose(); }
        }
    }

    private void validateDocument(Path path, String ext) throws IOException {
        byte[] header;
        try (var input = Files.newInputStream(path)) { header = input.readNBytes(8); }
        if ("pdf".equals(ext) && !new String(header, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-")) {
            throw new ServiceException("文件不是有效的PDF");
        }
        if (Set.of("docx", "xlsx").contains(ext)
                && (header.length < 4 || header[0] != 'P' || header[1] != 'K')) {
            throw new ServiceException("文件内容与扩展名不一致");
        }
        if (Set.of("docx", "xlsx").contains(ext)) {
            try (var zip = new java.util.zip.ZipFile(path.toFile())) {
                var entries = zip.entries();
                long expandedBytes = 0;
                int count = 0;
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.getSize() < 0 || ++count > 1000
                            || (expandedBytes += entry.getSize()) > 32L * 1024 * 1024) {
                        throw new ServiceException("文档解压后体积过大，请拆分文件");
                    }
                }
            }
        }
        if ("xls".equals(ext) && (header.length < 8
                || header[0] != (byte) 0xd0 || header[1] != (byte) 0xcf
                || header[2] != (byte) 0x11 || header[3] != (byte) 0xe0)) {
            throw new ServiceException("文件不是有效的Excel文档");
        }
        if (!Set.of("pdf", "docx", "xlsx", "xls").contains(ext)) {
            String text = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            if (text.indexOf('\0') >= 0) throw new ServiceException("文本附件含无效二进制内容，请使用UTF-8文本");
        }
    }

    private static String imageMediaType(String ext) { return "image/" + ("jpg".equals(ext) ? "jpeg" : ext); }
    private static String documentMediaType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            default -> "text/plain;charset=UTF-8";
        };
    }
}
