package com.polaris.platform.service;

import com.polaris.ai.customer.application.CustomerServiceAvatarConfig;
import com.polaris.common.config.PolarisConfig;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceAvatarAsset;
import com.polaris.platform.mapper.CustomerServiceAvatarAssetMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** 私有且不可变的人物素材；只接收本地上传，不代取远程 URL。 */
@Service
public class CustomerServiceAvatarAssetService {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final CustomerServiceAvatarAssetMapper mapper;
    private final CustomerServiceAccess access;
    private final Environment environment;

    public CustomerServiceAvatarAssetService(CustomerServiceAvatarAssetMapper mapper, CustomerServiceAccess access, Environment environment) {
        this.mapper = mapper; this.access = access; this.environment = environment;
    }

    public List<AssetView> list() { return mapper.list(access.requireAdmin().tenantId()).stream().map(this::view).toList(); }

    @Transactional(rollbackFor = Exception.class)
    public AssetView upload(String name, byte[] bytes) {
        var actor = access.requireAdmin();
        if (name == null || name.isBlank() || name.length() > 180 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl)) throw invalid();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
        String type = detect(bytes);
        if (!Objects.equals(mapper.lockTenant(actor.tenantId()), actor.tenantId())) throw missing();
        // 使用当前锁定读，不沿用管理员查询可能建立的REPEATABLE READ旧快照。
        var existing = mapper.lockAssets(actor.tenantId());
        if (existing.size() >= 100 || existing.stream().mapToLong(CustomerServiceAvatarAsset::getByteSize).sum() + bytes.length > 200L * 1024 * 1024) {
            throw new ServiceException("ASSET_LIMIT: 人物素材已达租户容量上限（100份或200MiB），请复用已有素材", 429);
        }
        var asset = new CustomerServiceAvatarAsset();
        asset.setId(UUID.randomUUID().toString()); asset.setTenantId(actor.tenantId()); asset.setName(name.strip());
        asset.setKind(type.equals("video/mp4") ? "VIDEO" : type.equals("image/gif") ? "GIF" : "IMAGE");
        asset.setContentType(type); asset.setByteSize((long) bytes.length); asset.setContentHash(digest(bytes));
        asset.setCreateBy(actor.userId()); asset.setCreateTime(new Date());
        Path target = null;
        boolean created = false;
        try {
            target = path(asset, true);
            try (var channel = Files.newByteChannel(target, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
            if (mapper.insert(asset) != 1) throw missing();
            Path written = target;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) discard(written); }
                });
            }
            return view(asset);
        } catch (RuntimeException | java.io.IOException failure) {
            if (created) discard(target);
            throw new ServiceException("ASSET_UNAVAILABLE: 人物素材未保存，请检查私有存储后重试", 409);
        }
    }

    /** 草稿与发布均检查当前租户引用；旧的无人物配置不会查询素材表。 */
    public void validate(CustomerServiceAvatarConfig config) {
        if (config == null) return;
        Long tenantId = access.requireAdmin().tenantId();
        for (String id : config.assetIds()) {
            var asset = require(tenantId, id);
            if (id.equals(config.coverAssetId()) && !"IMAGE".equals(asset.getKind())) {
                throw new ServiceException("INVALID_INPUT: 人物封面必须是静态PNG或JPEG图片", 400);
            }
            try { if (!Files.isRegularFile(path(asset, false), LinkOption.NOFOLLOW_LINKS)) throw missing(); }
            catch (java.io.IOException failure) { throw missing(); }
        }
    }

    public AssetData read(String id) {
        return readTenant(access.requireAdmin().tenantId(), id);
    }

    /** 调用者仍需提供已验证会话，且素材必须是固定发布快照的引用。 */
    public AssetData readVisitor(String id, String sessionId, CustomerServiceConversationService conversations) {
        var config = conversations.avatar(sessionId);
        if (config == null || !config.assetIds().contains(id)
                || !(com.polaris.ai.core.context.CallerContextHolder.get() instanceof com.polaris.ai.core.context.CustomerServiceVisitorContext visitor)) throw missing();
        var data = readTenant(visitor.tenantId(), id);
        conversations.avatar(sessionId);
        return data;
    }

    private AssetData readTenant(Long tenantId, String id) {
        var asset = require(tenantId, id);
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(path(asset, false), LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length != asset.getByteSize() || bytes.length > MAX_BYTES || !digest(bytes).equals(asset.getContentHash())) throw missing();
            return new AssetData(asset.getContentType(), bytes);
        } catch (java.io.IOException failure) { throw missing(); }
    }

    private CustomerServiceAvatarAsset require(Long tenantId, String id) {
        if (id == null || !id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw missing();
        var asset = mapper.select(tenantId, id);
        if (asset == null || !tenantId.equals(asset.getTenantId()) || !id.equals(asset.getId())
                || asset.getByteSize() == null || asset.getByteSize() < 1 || asset.getByteSize() > MAX_BYTES
                || asset.getContentType() == null || !Set.of("image/png", "image/jpeg", "image/gif", "video/mp4").contains(asset.getContentType())
                || !Objects.equals(asset.getKind(), asset.getContentType().equals("video/mp4") ? "VIDEO" : asset.getContentType().equals("image/gif") ? "GIF" : "IMAGE")) throw missing();
        return asset;
    }

    private Path path(CustomerServiceAvatarAsset asset, boolean create) throws java.io.IOException {
        String configured = environment.getProperty("ai.moderation.storage-root");
        String profile = PolarisConfig.getProfile();
        if ((configured == null || configured.isBlank()) && (profile == null || profile.isBlank())) throw missing();
        Path root = Path.of(configured == null || configured.isBlank() ? profile + "-private" : configured).toAbsolutePath().normalize();
        if ((profile != null && root.startsWith(Path.of(profile).toAbsolutePath().normalize())) || Files.isSymbolicLink(root)) throw missing();
        if (create) Files.createDirectories(root);
        Path base = root.toRealPath();
        if (profile != null && !profile.isBlank()) {
            Path publicRoot = Path.of(profile).toAbsolutePath().normalize();
            if (Files.exists(publicRoot)) publicRoot = publicRoot.toRealPath();
            if (base.startsWith(publicRoot)) throw missing();
        }
        Path folder = base.resolve("customer-avatar");
        if (Files.isSymbolicLink(folder)) throw missing();
        if (create) Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (!folder.toRealPath().equals(base.resolve("customer-avatar"))) throw missing();
        Path tenant = folder.resolve(Long.toString(asset.getTenantId()));
        if (Files.isSymbolicLink(tenant)) throw missing();
        if (create) Files.createDirectories(tenant, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (!tenant.toRealPath().equals(folder.resolve(Long.toString(asset.getTenantId())))) throw missing();
        return tenant.resolve(asset.getId());
    }

    private String detect(byte[] bytes) {
        if (bytes.length >= 24 && tag(bytes, 4, "ftyp")) {
            long first = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 0, 4).getInt());
            if (first < 16 || first > 128 || first > bytes.length || !Set.of("isom", "iso2", "mp41", "mp42", "avc1", "M4V ").contains(tag(bytes, 8, 4))) throw invalid();
            boolean movie = false, data = false;
            for (int offset = 0; offset < bytes.length;) {
                if (bytes.length - offset < 8) throw invalid();
                long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).getInt());
                int header = 8;
                if (size == 1) {
                    if (bytes.length - offset < 16) throw invalid();
                    size = ByteBuffer.wrap(bytes, offset + 8, 8).getLong(); header = 16;
                } else if (size == 0) size = bytes.length - offset;
                if (size < header || size > bytes.length - offset) throw invalid();
                movie |= tag(bytes, offset + 4, "moov"); data |= tag(bytes, offset + 4, "mdat");
                offset += (int) size;
            }
            if (!movie || !data) throw invalid();
            return "video/mp4";
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 8_000_000) throw invalid();
                if (format.equals("png")) {
                    // 不把APNG当作静态兜底封面；所有PNG块必须在上传长度以内。
                    for (int offset = 8; offset < bytes.length;) {
                        if (bytes.length - offset < 12) throw invalid();
                        long length = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).getInt());
                        if (length > bytes.length - offset - 12 || tag(bytes, offset + 4, "acTL")) throw invalid();
                        offset += (int) length + 12;
                    }
                    return "image/png";
                }
                if (format.equals("jpeg")) return "image/jpeg";
                if (format.equals("gif")) return "image/gif";
                throw invalid();
            } finally { reader.dispose(); }
        } catch (java.io.IOException failure) { throw invalid(); }
    }

    private static boolean tag(byte[] bytes, int offset, String value) { return value.equals(tag(bytes, offset, value.length())); }
    private static String tag(byte[] bytes, int offset, int length) { return offset >= 0 && offset + length <= bytes.length ? new String(bytes, offset, length, StandardCharsets.US_ASCII) : ""; }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void discard(Path path) {
        try { Files.deleteIfExists(path); }
        catch (java.io.IOException failure) { org.slf4j.LoggerFactory.getLogger(CustomerServiceAvatarAssetService.class).warn("人物上传回滚后的私有文件未清理，需检查存储"); }
    }
    private AssetView view(CustomerServiceAvatarAsset asset) { return new AssetView(asset.getId(), asset.getName(), asset.getKind(), asset.getContentType(), asset.getByteSize()); }
    private ServiceException missing() { return new ServiceException("ASSET_UNAVAILABLE: 人物素材不存在、已损坏或无权访问", 404); }
    private ServiceException invalid() { return new ServiceException("INVALID_INPUT: 仅支持8MiB以内的PNG/JPEG、GIF及MP4；图片最长边4096、最多800万像素", 400); }
    public record AssetView(String id, String name, String kind, String contentType, long byteSize) {}
    public record AssetData(String contentType, byte[] bytes) {}
}
