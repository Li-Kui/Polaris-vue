package com.polaris.ai.knowledge;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.KnowledgeDocumentWorkerMapper;
import com.polaris.ai.mapper.KnowledgeRebuildTaskMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** 只在文档处理期间安装作用域。模型/向量网络调用不持有数据库行锁。 */
@Service
public class KnowledgeDocumentWorkerFence {
    private final KnowledgeDocumentWorkerMapper workers;
    private final KnowledgeRebuildTaskMapper parents;
    private final TransactionTemplate transactions;
    private final ThreadLocal<Handle> current = new ThreadLocal<>();

    public KnowledgeDocumentWorkerFence(KnowledgeDocumentWorkerMapper workers, KnowledgeRebuildTaskMapper parents,
            PlatformTransactionManager manager) {
        this.workers = workers; this.parents = parents; this.transactions = new TransactionTemplate(manager);
    }

    public void run(AiKnowledgeBase source, AiDocument document, Runnable action) {
        if (current.get() != null) throw lost();
        AiKnowledgeBase frozen = new AiKnowledgeBase(); BeanUtils.copyProperties(source, frozen);
        AiDocument doc = new AiDocument(); BeanUtils.copyProperties(document, doc);
        if (frozen.getIndexGeneration() == null || !Objects.equals(doc.getKnowledgeBaseId(), frozen.getId())) throw lost();
        Handle handle = new Handle(frozen, doc, UUID.randomUUID().toString());
        transactions.executeWithoutResult(status -> {
            requireParent(handle.source());
            if (workers.claim(frozen, doc, handle.owner()) != 1
                    || !sameDocument(doc, workers.lockOwned(frozen, doc, handle.owner()))) throw lost();
        });
        try {
            current.set(handle);
            action.run();
        } catch (RuntimeException | Error e) {
            // 不用迟到异常覆盖新代次/新 owner；错误文本不把厂商响应或私有路径写入文档备注。
            fail(handle);
            throw e;
        } finally {
            current.remove();
            transactions.executeWithoutResult(status -> {
                if (parents.lockParent(frozen) != null) workers.release(frozen, doc, handle.owner());
            });
        }
    }

    public void check() {mutate(() -> {});}

    public void mutate(Runnable action) {readOrWrite(() -> {action.run(); return null;});}

    public <T> T readOrWrite(Supplier<T> action) {
        Handle h = current.get(); if (h == null) throw lost();
        return transactions.execute(status -> {
            requireParent(h.source());
            if (!sameDocument(h.document(), workers.lockOwned(h.source(), h.document(), h.owner()))
                    || workers.heartbeat(h.source(), h.document(), h.owner()) != 1) throw lost();
            return action.get();
        });
    }

    /** 向量上下文准备在领取文档前失败时，仅允许标记当前代次、尚未被领取的待解析行。 */
    public void failPending(AiKnowledgeBase source, AiDocument document) {
        if (source == null || document == null || source.getIndexGeneration() == null) return;
        transactions.executeWithoutResult(status -> {
            if (matches(source, parents.lockParent(source)) && sameDocument(document, workers.lockPending(source, document)))
                workers.failPending(source, document);
        });
    }

    private void fail(Handle h) {
        transactions.executeWithoutResult(status -> {
            AiKnowledgeBase parent = parents.lockParent(h.source());
            if (parent == null || !Objects.equals(parent.getTenantId(), h.source().getTenantId())) return;
            // 允许过期/来源代次变化后收尾自己尚未完成的行，避免遗留“处理中”。
            // 不能写成功或安全结果；新 owner、已成功、已隔离/删除或替换来源均拒绝。
            if (sameDocument(h.document(), workers.lockForFailure(h.source(), h.document(), h.owner())))
                workers.failOwned(h.source(), h.document(), h.owner());
        });
    }

    private void requireParent(AiKnowledgeBase source) {
        if (!matches(source, parents.lockParent(source))) throw lost();
    }

    private boolean sameDocument(AiDocument expected, AiDocument actual) {
        // MySQL 字符串列可能大小写不敏感；文件来源必须按 Java 精确比较，不能只依赖 SQL 等号。
        return actual != null && Objects.equals(expected.getId(), actual.getId())
                && Objects.equals(expected.getKnowledgeBaseId(), actual.getKnowledgeBaseId())
                && Objects.equals(expected.getName(), actual.getName()) && Objects.equals(expected.getFileUrl(), actual.getFileUrl());
    }

    private boolean matches(AiKnowledgeBase source, AiKnowledgeBase actual) {
        return actual != null && Objects.equals(source.getId(), actual.getId())
                && Objects.equals(source.getTenantId(), actual.getTenantId())
                && Objects.equals(source.getIndexGeneration(), actual.getIndexGeneration())
                && Objects.equals(source.getIndexVersion(), actual.getIndexVersion())
                && Objects.equals(source.getVectorCollection(), actual.getVectorCollection())
                && ("READY".equals(actual.getIndexStatus()) || "BUILDING".equals(actual.getIndexStatus()));
    }

    private IllegalStateException lost() {return new IllegalStateException("文档处理来源或所有者已变化，旧 worker 停止回写，请刷新诊断");}
    private record Handle(AiKnowledgeBase source, AiDocument document, String owner) { }
}
