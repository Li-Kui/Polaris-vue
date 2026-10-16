package com.polaris.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.KnowledgeDocumentSnapshotMapper;
import com.polaris.ai.safety.service.PrivateStoragePaths;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** 载荷只写入私有目录；先保存 PREPARED，全部向量写入完成后才标记 READY。 */
@Service
@Slf4j
public class KnowledgeDocumentSnapshotStore {
    private static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
    private final KnowledgeDocumentSnapshotMapper mapper;
    private final PrivateStoragePaths paths;
    private final ObjectMapper json;

    public KnowledgeDocumentSnapshotStore(KnowledgeDocumentSnapshotMapper mapper, PrivateStoragePaths paths, ObjectMapper json) {
        this.mapper = mapper; this.paths = paths; this.json = json;
    }

    public KnowledgeDocumentSnapshot prepare(AiKnowledgeBase kb, AiDocument doc, Long version, String collection,
                                             KnowledgeDocumentParser.Result parsed, List<TextSegment> segments, List<String> pointIds) {
        if (segments.isEmpty() || segments.size() > 2000 || pointIds.size() != segments.size()) throw new IllegalStateException("SNAPSHOT_LIMIT");
        KnowledgeDocumentSnapshot row = row(kb, doc, version, collection);
        row.setFileHash(parsed.fileHash()); row.setContentHash(parsed.contentHash());
        row.setParsedChars(parsed.content().length()); row.setChunkCount(segments.size());
        row.setSnapshotState("PREPARED");
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            List<String> locations = parsed.blocks().stream().filter(block -> block.text().contains(segment.text()))
                    .map(KnowledgeDocumentParser.Block::location).toList();
            // 只有单块内的唯一精确匹配才标注位置；跨块/重复正文不猜页码。
            chunks.add(new Chunk(segment.metadata().getString("chunk_no"), pointIds.get(i), segment.text(),
                    digest(segment.text()), locations.size() == 1 ? locations : List.of()));
        }
        try {
            row.setWarningsJson(json.writeValueAsString(parsed.warnings()));
            byte[] bytes = json.writeValueAsBytes(new Payload(parsed.blocks(), List.copyOf(chunks)));
            if (bytes.length > MAX_PAYLOAD_BYTES) throw new IllegalStateException("SNAPSHOT_LIMIT");
            row.setPayloadHash(KnowledgeDocumentParser.sha256(bytes));
            Path target = payloadPath(row.getSnapshotKey(), true);
            try (var channel = Files.newByteChannel(target, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
            if (mapper.insert(row) != 1) throw new IllegalStateException("SNAPSHOT_SAVE_FAILED");
            return row;
        } catch (Exception e) {
            deletePayload(row.getSnapshotKey());
            throw new IllegalStateException("SNAPSHOT_SAVE_FAILED");
        }
    }

    public void ready(KnowledgeDocumentSnapshot row) {
        int changed = mapper.update(null, new LambdaUpdateWrapper<KnowledgeDocumentSnapshot>()
                .eq(KnowledgeDocumentSnapshot::getId, row.getId()).eq(KnowledgeDocumentSnapshot::getSnapshotState, "PREPARED")
                .set(KnowledgeDocumentSnapshot::getSnapshotState, "READY"));
        if (changed != 1) throw new IllegalStateException("SNAPSHOT_STATE_CHANGED");
    }

    public void failed(KnowledgeDocumentSnapshot row) {
        // 只收尾本次未完成快照，不能用迟到异常降级 READY；数据库失败时也不先删物理文件。
        LambdaUpdateWrapper<KnowledgeDocumentSnapshot> update = new LambdaUpdateWrapper<KnowledgeDocumentSnapshot>()
                .eq(KnowledgeDocumentSnapshot::getId, row.getId()).eq(KnowledgeDocumentSnapshot::getSnapshotKey, row.getSnapshotKey())
                .eq(KnowledgeDocumentSnapshot::getSnapshotState, "PREPARED")
                .set(KnowledgeDocumentSnapshot::getSnapshotState, "FAILED").set(KnowledgeDocumentSnapshot::getErrorCode, "INDEX_WRITE_FAILED");
        queueCleanup(update, row);
        mapper.update(null, update);
    }

    public void parseFailed(AiKnowledgeBase kb, AiDocument doc, Long version, String collection, String code) {
        KnowledgeDocumentSnapshot row = row(kb, doc, version, collection);
        row.setSnapshotState("FAILED"); row.setErrorCode(code); row.setWarningsJson("[]");
        row.setChunkCount(0); row.setParsedChars(0);
        if (mapper.insert(row) != 1) throw new IllegalStateException("SNAPSHOT_SAVE_FAILED");
    }

    public KnowledgeDocumentSnapshot latest(AiKnowledgeBase kb, AiDocument doc, boolean currentOnly) {
        LambdaQueryWrapper<KnowledgeDocumentSnapshot> query = new LambdaQueryWrapper<KnowledgeDocumentSnapshot>()
                .eq(KnowledgeDocumentSnapshot::getKnowledgeBaseId, kb.getId()).eq(KnowledgeDocumentSnapshot::getDocumentId, doc.getId());
        if (kb.getTenantId() == null) query.isNull(KnowledgeDocumentSnapshot::getTenantId);
        else query.eq(KnowledgeDocumentSnapshot::getTenantId, kb.getTenantId());
        if (currentOnly) query.eq(KnowledgeDocumentSnapshot::getIndexVersion, kb.getIndexVersion())
                .eq(KnowledgeDocumentSnapshot::getCollectionHash, digest(kb.getVectorCollection()));
        List<KnowledgeDocumentSnapshot> rows = mapper.selectList(query.orderByDesc(KnowledgeDocumentSnapshot::getId).last("LIMIT 1"));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    public Payload load(KnowledgeDocumentSnapshot row) {
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(payloadPath(row.getSnapshotKey(), false), LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_PAYLOAD_BYTES + 1);
            }
            if (bytes.length > MAX_PAYLOAD_BYTES || !Objects.equals(row.getPayloadHash(), KnowledgeDocumentParser.sha256(bytes)))
                throw new IllegalStateException("SNAPSHOT_INVALID");
            Payload payload = json.readValue(bytes, Payload.class);
            if (payload == null || payload.chunks() == null || payload.chunks().size() != row.getChunkCount())
                throw new IllegalStateException("SNAPSHOT_INVALID");
            return payload;
        } catch (Exception e) {throw new IllegalStateException("SNAPSHOT_UNAVAILABLE");}
    }

    public boolean sourceMatches(AiDocument doc, KnowledgeDocumentSnapshot row) {
        try {
            Path source = Path.of(doc.getFileUrl());
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return false;
            try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(KnowledgeDocumentParser.MAX_FILE_BYTES + 1);
                return bytes.length <= KnowledgeDocumentParser.MAX_FILE_BYTES && Objects.equals(row.getFileHash(), KnowledgeDocumentParser.sha256(bytes));
            }
        } catch (Exception e) {return false;}
    }

    public void retire(Long knowledgeId, Long documentId) {
        long cursor = 0;
        while (true) {
            LambdaQueryWrapper<KnowledgeDocumentSnapshot> query = new LambdaQueryWrapper<KnowledgeDocumentSnapshot>()
                    .eq(KnowledgeDocumentSnapshot::getKnowledgeBaseId, knowledgeId).gt(KnowledgeDocumentSnapshot::getId, cursor)
                    .ne(KnowledgeDocumentSnapshot::getSnapshotState, "RETIRED");
            if (documentId != null) query.eq(KnowledgeDocumentSnapshot::getDocumentId, documentId);
            List<KnowledgeDocumentSnapshot> rows = mapper.selectList(query.orderByAsc(KnowledgeDocumentSnapshot::getId).last("LIMIT 100"));
            if (rows == null || rows.isEmpty()) return;
            for (KnowledgeDocumentSnapshot row : rows) {
                LambdaUpdateWrapper<KnowledgeDocumentSnapshot> update = new LambdaUpdateWrapper<KnowledgeDocumentSnapshot>()
                        .eq(KnowledgeDocumentSnapshot::getId, row.getId()).ne(KnowledgeDocumentSnapshot::getSnapshotState, "RETIRED")
                        .set(KnowledgeDocumentSnapshot::getSnapshotState, "RETIRED");
                queueCleanup(update, row);
                mapper.update(null, update);
                cursor = row.getId();
            }
        }
    }

    private void queueCleanup(LambdaUpdateWrapper<KnowledgeDocumentSnapshot> update, KnowledgeDocumentSnapshot row) {
        if (row.getPayloadHash() != null && "NONE".equals(row.getPayloadCleanupState()))
            update.set(KnowledgeDocumentSnapshot::getPayloadCleanupState, "PENDING")
                    .setSql("payload_cleanup_after = TIMESTAMPADD(HOUR, 24, CURRENT_TIMESTAMP)");
    }

    private KnowledgeDocumentSnapshot row(AiKnowledgeBase kb, AiDocument doc, Long version, String collection) {
        KnowledgeDocumentSnapshot row = new KnowledgeDocumentSnapshot(); row.setSnapshotKey(UUID.randomUUID().toString());
        row.setTenantId(kb.getTenantId()); row.setKnowledgeBaseId(kb.getId()); row.setDocumentId(doc.getId());
        row.setIndexVersion(version); row.setCollectionHash(digest(collection)); row.setParserVersion(KnowledgeDocumentParser.VERSION);
        row.setSplitterVersion("RECURSIVE_V1"); row.setCreateTime(new Date()); return row;
    }
    private Path payloadPath(String key, boolean create) throws Exception {
        if (key == null || !key.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw new SecurityException("SNAPSHOT_INVALID");
        Path directory = paths.privateRoot().resolve("knowledge/snapshots");
        if (Files.isSymbolicLink(paths.privateRoot().resolve("knowledge")) || Files.isSymbolicLink(directory)) throw new SecurityException("SNAPSHOT_INVALID");
        if (create) Files.createDirectories(directory);
        // 拒绝私有子目录的符号链接逃逸；文件名仅来自服务端 UUID。
        if (!directory.toRealPath().equals(paths.privateRoot().toRealPath().resolve("knowledge/snapshots"))) throw new SecurityException("SNAPSHOT_INVALID");
        if (create) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return directory.resolve(key + ".json");
    }
    private boolean deletePayload(String key) {
        boolean success=cleanupPayload(key)==PayloadDeletion.DONE;
        if (!success) log.warn("知识库私有快照清理未完成，需后续重试；不提供 FAILED/RETIRED 正文");
        return success;
    }
    /** 只能由已鉴权并锁定失败/退役元数据的清理服务调用，不接受用户路径。缺失文件幂等成功。 */
    public PayloadDeletion cleanupPayload(String key) {
        Path target;
        try {target=payloadPath(key,false);}
        catch (SecurityException e) {return PayloadDeletion.INVALID_PATH;}
        catch (Exception e) {return PayloadDeletion.RETRY;} // 根目录/挂载不可用，不能冒充每个文件已经清理。
        try {
            var attributes=Files.readAttributes(target,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) return PayloadDeletion.INVALID_PATH;
            Files.delete(target);return PayloadDeletion.DONE;
        } catch (NoSuchFileException e) {return PayloadDeletion.DONE;}
        catch (SecurityException e) {return PayloadDeletion.INVALID_PATH;}
        catch (Exception e) {return PayloadDeletion.RETRY;}
    }
    public enum PayloadDeletion {DONE, RETRY, INVALID_PATH}
    public static String digest(String value) {return KnowledgeDocumentParser.sha256(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));}
    public record Chunk(String chunkNo, String pointId, String text, String contentHash, List<String> locations) { }
    public record Payload(List<KnowledgeDocumentParser.Block> blocks, List<Chunk> chunks) { }
}
