package com.polaris.ai.knowledge;

import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.KnowledgeRebuildTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/** 手动/启动构建和增量导入共用任务作用域；不持事务锁调用外部服务。 */
@Service
public class KnowledgeRebuildTaskFence {
    private final KnowledgeRebuildTaskMapper tasks;
    private final TransactionTemplate transactions;
    private final ThreadLocal<Handle> current = new ThreadLocal<>();
    public KnowledgeRebuildTaskFence(KnowledgeRebuildTaskMapper tasks, PlatformTransactionManager manager) {
        this.tasks = tasks; this.transactions = new TransactionTemplate(manager);
    }
    public void run(Handle handle, Runnable work) {
        if (current.get() != null) throw new IllegalStateException("不允许嵌套重建任务");
        try {current.set(handle); work.run();} finally {current.remove();}
    }
    public void check() {
        Handle h = current.get();
        if (h != null && tasks.heartbeat(h.source(), h.id(), h.owner()) != 1) throw lost();
    }
    public void manifest(List<KnowledgeIndexPublisher.Entry> manifest) {
        Handle h = current.get(); if (h == null) return;
        String ids = "[" + String.join(",", manifest.stream().map(e -> String.valueOf(e.id())).toList()) + "]";
        if (tasks.manifest(h.source(), h.id(), h.owner(), ids, manifest.size()) != 1) throw lost();
    }
    public void completed() {
        Handle h = current.get();
        if (h != null && tasks.progress(h.source(), h.id(), h.owner()) != 1) throw lost();
    }
    /** 增量导入最后一次文档事务内，同时提交文档/快照及任务成功。全库任务仍在发布时完成。 */
    public void completeImport(Runnable action) {
        Handle h = current.get();
        if (h == null || !h.incremental()) {action.run(); return;}
        publish(() -> {action.run(); completed();});
    }
    public void publish(Runnable action) {
        Handle h = current.get(); if (h == null) {action.run(); return;}
        transactions.executeWithoutResult(status -> {
            // 与提交/显式恢复保持相同锁序：先父库，再任务；最终发布与任务成功同事务。
            if (tasks.lockParent(h.source()) == null) throw lost();
            KnowledgeRebuildTask row = tasks.lock(h.source(), h.id());
            if (!owned(row, h) || row.isExpired()) throw lost();
            action.run();
            if (tasks.finish(h.source(), h.id(), h.owner(), "SUCCEEDED", null) != 1) throw lost();
        });
    }
    public void failed(Handle h) {
        transactions.executeWithoutResult(status -> {
            AiKnowledgeBase parent = tasks.lockParent(h.source());
            KnowledgeRebuildTask row = tasks.lock(h.source(), h.id());
            if (!owned(row, h)) return; // 已成功、已恢复或所有者已变，迟到失败不得覆盖。
            String state = row.isExpired() ? "INTERRUPTED"
                    : parent == null || !Objects.equals(parent.getIndexGeneration(), row.getClaimedGeneration()) ? "INVALIDATED" : "FAILED";
            tasks.finish(h.source(), h.id(), h.owner(), state,
                    "INTERRUPTED".equals(state) ? "LEASE_EXPIRED" : "INVALIDATED".equals(state) ? "SOURCE_CHANGED" : "BUILD_FAILED");
        });
    }
    private boolean owned(KnowledgeRebuildTask row, Handle h) {
        return row != null && "RUNNING".equals(row.getTaskState()) && Objects.equals(row.getOwnerToken(), h.owner());
    }
    private IllegalStateException lost() {return new IllegalStateException("重建任务租约已失效，停止旧任务；请刷新任务状态后显式重新构建");}
    public record Handle(AiKnowledgeBase source, String id, String owner, boolean incremental) {
        public Handle(AiKnowledgeBase source, String id, String owner) {this(source, id, owner, false);}
    }
}
