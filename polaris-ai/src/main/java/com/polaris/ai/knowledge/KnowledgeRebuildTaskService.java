package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.KnowledgeRebuildTaskMapper;
import com.polaris.ai.safety.service.PrivateStoragePaths;
import com.polaris.ai.service.IAiKnowledgeService;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 持久化导入/重建；共用活动槽，重启后不自动派发过期任务或复用未知外部结果。 */
@Service
public class KnowledgeRebuildTaskService {
    private final KnowledgeRebuildTaskMapper tasks;
    private final KnowledgeIndexPublisher publisher;
    private final KnowledgeRebuildTaskFence fence;
    private final ObjectProvider<IAiKnowledgeService> knowledge;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;
    private final PrivateStoragePaths storage;
    private final String startupKey = "startup_" + UUID.randomUUID();

    public KnowledgeRebuildTaskService(KnowledgeRebuildTaskMapper tasks, KnowledgeIndexPublisher publisher,
            KnowledgeRebuildTaskFence fence, ObjectProvider<IAiKnowledgeService> knowledge,
            @Qualifier("knowledgeRebuildExecutor") TaskExecutor executor, PlatformTransactionManager manager,
            PrivateStoragePaths storage) {
        this.tasks = tasks; this.publisher = publisher; this.fence = fence; this.knowledge = knowledge;
        this.executor = executor; this.transactions = new TransactionTemplate(manager);
        this.storage = storage;
    }

    public View submit(Long knowledgeId, String requestKey, String recoverTaskId) {
        AiKnowledgeBase source = requireAccessible(knowledgeId);
        String key = requestKey(requestKey);
        if (recoverTaskId != null) validateTaskId(recoverTaskId);
        AtomicBoolean created = new AtomicBoolean();
        KnowledgeRebuildTask task = transactions.execute(status -> {
            AiKnowledgeBase parent = tasks.lockParent(source);
            if (parent == null) throw new ServiceException("知识库不存在或无权访问", 404);
            KnowledgeRebuildTask duplicate = tasks.byRequest(parent, key);
            if (duplicate != null) {
                if (!"REBUILD".equals(duplicate.getTaskType())) throw new ServiceException("提交键已用于其他类型任务", 409);
                return duplicate; // 已结束的相同键也不能再次收费。
            }
            KnowledgeRebuildTask active = tasks.active(parent);
            if (recoverTaskId != null) {
                if (active == null || !Objects.equals(active.getTaskId(), recoverTaskId) || !active.isExpired())
                    throw new ServiceException("仅可显式恢复当前已过期的任务，请刷新状态", 409);
                if (tasks.expire(parent, recoverTaskId) != 1) throw new ServiceException("任务已变化，请刷新", 409);
                publisher.documentChanged(parent); // 同事务先使旧 worker 代次失效，再允许新任务领取。
                parent = tasks.lockParent(parent);
            } else if (active != null) {
                if (active.isExpired()) throw new ServiceException("旧任务已中断，请确认成本后显式重新构建", 409);
                // 不把未持久化的新键冒充已受理；否则活动任务结束后重试新键可能重复收费。
                throw new ServiceException("已有活动重建任务，请刷新任务状态；本次新提交未受理", 409);
            } else if ("BUILDING".equals(parent.getIndexStatus())) {
                throw new ServiceException("知识库存在非任务化构建，请等待结束后再提交", 409);
            }
            if (parent.getIndexGeneration() == null) throw new ServiceException("请先执行 SQL 22", 409);
            String username = Objects.toString(CallerContextHolder.require().getUsername(), "unknown");
            KnowledgeRebuildTask row = saveTask(parent, key, "REBUILD", null, username);
            created.set(true); return row;
        });
        if (created.get()) {
            if (!dispatch(source, task, false)) {
                throw new ServiceException("重建队列已满或暂不可用，任务未运行；请稍后使用新提交键重试", 503);
            }
        }
        return view(tasks.find(source, task.getTaskId()));
    }

    /** 文件先私有暂存；文档、父库代次和任务在同一事务提交，提交后才派发。 */
    public UploadView upload(Long knowledgeId, MultipartFile file, String requestKey) {
        AiKnowledgeBase source = requireAccessible(knowledgeId);
        String key = requestKey(requestKey);
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new ServiceException("上传文件不可为空，且不得超过 10MB", 400);
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl))
            throw new ServiceException("文件名无效或过长", 400);
        KnowledgeRebuildTask duplicate = tasks.byRequest(source, key);
        if (duplicate != null) return uploadView(duplicate);
        // 仅做早期拒绝优化；真正互斥在下方父库锁内重新检查。
        if (tasks.active(source) != null || "BUILDING".equals(source.getIndexStatus())) throw busy();
        Path staged;
        try {staged = storage.stageKnowledgeFile(file);} catch (Exception e) {throw new ServiceException("文件暂存失败，请稍后重试", 503);}
        AtomicBoolean attemptedDocument = new AtomicBoolean(), created = new AtomicBoolean();
        KnowledgeRebuildTask row;
        try {
            row = transactions.execute(status -> {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int completion) {
                            if (completion == STATUS_ROLLED_BACK) discard(staged);
                        }
                    });
                }
                AiKnowledgeBase parent = tasks.lockParent(source);
                if (parent == null) throw new ServiceException("知识库不存在或无权访问", 404);
                KnowledgeRebuildTask repeated = tasks.byRequest(parent, key);
                if (repeated != null) {uploadView(repeated); return repeated;}
                if (tasks.active(parent) != null || "BUILDING".equals(parent.getIndexStatus())) throw busy();
                AiDocument document = new AiDocument(); document.setKnowledgeBaseId(parent.getId());
                document.setName(name); document.setFileUrl(staged.toAbsolutePath().toString());
                document.setModerationStatus("WAIT_SCAN"); document.setCreateBy(CallerContextHolder.require().getUsername()); document.setCreateTime(new Date());
                attemptedDocument.set(true);
                if (knowledge.getObject().insertDocument(document) != 1 || document.getId() == null)
                    throw new IllegalStateException("文档保存失败");
                parent = tasks.lockParent(source); // insertDocument 已在同一事务推进父库代次。
                if (parent == null) throw new IllegalStateException("父库已失效");
                KnowledgeRebuildTask saved = saveTask(parent, key, "IMPORT", document.getId(), document.getCreateBy());
                created.set(true); return saved;
            });
        } catch (RuntimeException e) {
            // 确认未尝试写入文档时可立即清理；已写入的回滚由事务回调清理。
            // 提交结果不确定时保留文件，不能删掉可能已经提交的文档来源，后续孤儿治理处理。
            if (!attemptedDocument.get()) discard(staged);
            throw e;
        }
        if (!created.get()) discard(staged); // 并发同键已有任务，本次新暂存文件未被引用。
        else dispatch(source, row, false); // 拒绝仍返回已保存的文档/REJECTED 任务，不谎称文件未落库。
        return uploadView(tasks.find(source, row.getTaskId()));
    }

    /** 仅 ApplicationReady 内部入口；尊重既有启动策略，不抢占或恢复任何活动任务。 */
    public View submitStartup(AiKnowledgeBase source) {
        AtomicBoolean created = new AtomicBoolean();
        KnowledgeRebuildTask row = transactions.execute(status -> {
            AiKnowledgeBase parent = tasks.lockParent(source);
            if (parent == null) return null;
            KnowledgeRebuildTask existing = tasks.byRequest(parent, startupKey);
            if (existing != null) return existing;
            if (tasks.active(parent) != null || "BUILDING".equals(parent.getIndexStatus())) return null;
            KnowledgeRebuildTask saved = saveTask(parent, startupKey, "STARTUP", null, "knowledge-startup");
            created.set(true); return saved;
        });
        if (created.get()) dispatch(source, row, true);
        return view(row == null ? null : tasks.find(source, row.getTaskId()));
    }

    private KnowledgeRebuildTask saveTask(AiKnowledgeBase parent, String key, String type, Long documentId, String username) {
        if (parent.getIndexGeneration() == null) throw new ServiceException("请先执行 SQL 22", 409);
        KnowledgeRebuildTask row = new KnowledgeRebuildTask();
        row.setTaskId(UUID.randomUUID().toString()); row.setTenantId(parent.getTenantId()); row.setKnowledgeBaseId(parent.getId());
        row.setRequestKey(key); row.setSourceGeneration(parent.getIndexGeneration()); row.setTaskType(type); row.setDocumentId(documentId);
        String author = Objects.toString(username, "unknown"); row.setCreateBy(author.substring(0, Math.min(author.length(), 128)));
        if (tasks.insert(row) != 1) throw new IllegalStateException("索引任务保存失败");
        return tasks.find(parent, row.getTaskId());
    }
    private boolean dispatch(AiKnowledgeBase source, KnowledgeRebuildTask task, boolean startup) {
        AiKnowledgeBase frozen = new AiKnowledgeBase(); BeanUtils.copyProperties(source, frozen);
        try {
            executor.execute(() -> {
                if (!startup) {execute(frozen, task.getTaskId()); return;}
                CallerContext previous = CallerContextHolder.get();
                try {CallerContextHolder.set(new StartupCaller(frozen.getTenantId())); execute(frozen, task.getTaskId());}
                finally {CallerContextHolder.set(previous);}
            });
            return true;
        } catch (RuntimeException e) {tasks.reject(frozen, task.getTaskId()); return false;}
    }
    private String requestKey(String provided) {
        String key = provided == null ? UUID.randomUUID().toString() : provided;
        if (!key.matches("[A-Za-z0-9_-]{8,64}")) throw new ServiceException("幂等提交键格式无效", 400);
        return key;
    }
    private UploadView uploadView(KnowledgeRebuildTask task) {
        if (task == null || !"IMPORT".equals(task.getTaskType()) || task.getDocumentId() == null)
            throw new ServiceException("提交键已用于其他类型任务", 409);
        return new UploadView(task.getDocumentId(), view(task));
    }
    private ServiceException busy() {return new ServiceException("知识库已有活动索引任务，请等待结束；本次上传未受理", 409);}
    private void discard(Path path) {
        try {storage.discardStagedKnowledgeFile(path);} catch (Exception ignored) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("知识库暂存文件清理未完成，需后续孤儿治理");
        }
    }

    public View latest(Long knowledgeId) {return view(tasks.latest(requireAccessible(knowledgeId)));}
    public View get(Long knowledgeId, String taskId) {
        AiKnowledgeBase source = requireAccessible(knowledgeId); validateTaskId(taskId);
        KnowledgeRebuildTask task = tasks.find(source, taskId);
        if (task == null) throw new ServiceException("任务不存在或无权访问", 404);
        return view(task);
    }

    private void execute(AiKnowledgeBase source, String taskId) {
        String owner = UUID.randomUUID().toString();
        Work work;
        try {
            work = transactions.execute(status -> {
                AiKnowledgeBase parent = tasks.lockParent(source);
                KnowledgeRebuildTask task = tasks.lock(source, taskId);
                if (task == null || !"QUEUED".equals(task.getTaskState()) || task.isExpired()) return null;
                if (parent == null || !Objects.equals(parent.getIndexGeneration(), task.getSourceGeneration())
                        || "BUILDING".equals(parent.getIndexStatus())) {
                    tasks.invalidateQueued(source, taskId); return null;
                }
                boolean incremental = "IMPORT".equals(task.getTaskType()) && "READY".equals(parent.getIndexStatus())
                        && parent.getVectorCollection() != null;
                KnowledgeIndexPublisher.Build claimed = incremental ? null : publisher.begin(parent);
                long generation = incremental ? parent.getIndexGeneration() : claimed.generation();
                if (tasks.claim(source, taskId, owner, generation) != 1) throw new IllegalStateException("任务领取失败");
                return new Work(parent, claimed, task.getDocumentId(), incremental);
            });
        } catch (RuntimeException e) {tasks.reject(source, taskId); return;}
        if (work == null) return;
        var handle = new KnowledgeRebuildTaskFence.Handle(source, taskId, owner, work.incremental());
        try {
            fence.run(handle, () -> {
                if (work.incremental()) knowledge.getObject().runKnowledgeImportTask(work.source(), work.documentId());
                else knowledge.getObject().runKnowledgeRebuildTask(work.build());
            });
        } finally {
            // publish 已同事务置成功；失败收尾只可写同一个 RUNNING owner，不会覆盖成功/恢复任务。
            try {if (work.build() != null) publisher.failed(work.build());} finally {fence.failed(handle);}
        }
    }

    private AiKnowledgeBase requireAccessible(Long id) {
        var caller = CallerContextHolder.get();
        if (caller == null || caller.getUserId() == null || caller.getUserId() <= 0
                || !(caller.isSuperAdmin() || (caller.isPlatformMode() && caller.isTenantAdmin())))
            throw new ServiceException("仅系统超级管理员或当前租户管理员可管理重建任务", 403);
        if (id == null || id <= 0) throw new ServiceException("知识库标识无效", 400);
        AiKnowledgeBase parent = knowledge.getObject().selectAccessibleKnowledgeBaseById(id);
        if (parent == null || (!caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(), Objects.toString(parent.getTenantId(), null))))
            throw new ServiceException("知识库不存在或无权访问", 404);
        return parent;
    }
    private void validateTaskId(String id) {
        if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            throw new ServiceException("任务标识无效", 400);
    }
    private View view(KnowledgeRebuildTask row) {
        if (row == null) return null;
        boolean interrupted = row.isExpired() && ("QUEUED".equals(row.getTaskState()) || "RUNNING".equals(row.getTaskState()));
        return new View(row.getTaskId(), row.getKnowledgeBaseId(), interrupted ? "INTERRUPTED" : row.getTaskState(),
                row.getTotalDocuments(), row.getCompletedDocuments(), interrupted ? "LEASE_EXPIRED" : row.getErrorCode(),
                row.getCreateTime(), row.getStartTime(), row.getFinishTime(), interrupted, row.getTaskType(), row.getDocumentId());
    }
    public record View(String taskId, Long knowledgeBaseId, String state, Integer totalDocuments, Integer completedDocuments,
                       String errorCode, Date createTime, Date startTime, Date finishTime, boolean canRecover,
                       String taskType, Long documentId) { }
    public record UploadView(Long documentId, View task) { }
    private record Work(AiKnowledgeBase source, KnowledgeIndexPublisher.Build build, Long documentId, boolean incremental) { }
    private record StartupCaller(Long tenant) implements CallerContext {
        public Long getUserId() {return null;}
        public String getTenantId() {return Objects.toString(tenant, null);}
        public String getUsername() {return "knowledge-startup";}
        public Long getDeptId() {return null;}
        public boolean isSuperAdmin() {return false;}
    }
}
