package com.polaris.ai.workflow.runtime;

import com.polaris.ai.core.context.CallerUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** 工作流执行和审批状态的唯一持久化入口。 */
@Repository
public class WorkflowExecutionStore {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REJECTED = "REJECTED";

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowInstanceIdentity instanceIdentity;
    private final int leaseSeconds;

    public WorkflowExecutionStore(
            JdbcTemplate jdbcTemplate,
            WorkflowInstanceIdentity instanceIdentity,
            @Value("${ai.workflow.lease-seconds:90}") int leaseSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.instanceIdentity = instanceIdentity;
        this.leaseSeconds = Math.max(30, leaseSeconds);
    }

    public record Execution(
            String executionId,
            String workflowCode,
            int workflowVersion,
            String workflowSnapshot,
            Long tenantId,
            Long userId,
            Long conversationId,
            boolean testRun,
            String inputText,
            String fileUrl,
            String status) {
    }

    public record EventSequenceBlock(long first, long last) {
    }

    public void create(Execution execution) {
        jdbcTemplate.update(
                "INSERT INTO ai_workflow_execution " +
                        "(execution_id, workflow_code, workflow_version, workflow_snapshot, tenant_id, user_id, conversation_id, " +
                        "test_run, input_text, file_url, status, runner_id, lease_until, heartbeat_time, " +
                        "event_sequence, lock_version, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND), " +
                        "NOW(), 0, 0, NOW(), NOW())",
                execution.executionId(), execution.workflowCode(), execution.workflowVersion(),
                execution.workflowSnapshot(), execution.tenantId(), execution.userId(), execution.conversationId(),
                execution.testRun(), execution.inputText(), execution.fileUrl(), execution.status(),
                instanceIdentity.id(), leaseSeconds);
    }

    /** 以用户行为粒度串行化“检查配额 + 创建执行”，避免并发请求绕过配额。 */
    public boolean lockUserForExecution(Long userId) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            List<Long> users = jdbcTemplate.query(
                    "SELECT id FROM platform_tenant_user " +
                            "WHERE id = ? AND tenant_id = ? AND status = '0' FOR UPDATE",
                    (rs, rowNum) -> rs.getLong(1), userId, tenantId);
            return !users.isEmpty();
        }
        List<Long> users = jdbcTemplate.query(
                "SELECT user_id FROM sys_user WHERE user_id = ? AND status = '0' FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), userId);
        return !users.isEmpty();
    }

    public int countActiveByUser(Long userId) {
        Long tenantId = currentTenantId();
        Integer count = tenantId == null
                ? jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_workflow_execution WHERE tenant_id IS NULL " +
                                "AND user_id = ? AND status IN ('QUEUED','RUNNING')",
                        Integer.class, userId)
                : jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_workflow_execution WHERE tenant_id = ? " +
                                "AND user_id = ? AND status IN ('QUEUED','RUNNING')",
                        Integer.class, tenantId, userId);
        return count == null ? 0 : count;
    }

    public int countPendingApprovalsByUser(Long userId) {
        Long tenantId = currentTenantId();
        Integer count = tenantId == null
                ? jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_workflow_execution WHERE tenant_id IS NULL " +
                                "AND user_id = ? AND status = 'WAITING_APPROVAL'",
                        Integer.class, userId)
                : jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_workflow_execution WHERE tenant_id = ? " +
                                "AND user_id = ? AND status = 'WAITING_APPROVAL'",
                        Integer.class, tenantId, userId);
        return count == null ? 0 : count;
    }

    public Optional<Execution> findOwned(String executionId, Long userId) {
        Long tenantId = currentTenantId();
        String select = "SELECT execution_id, workflow_code, workflow_version, workflow_snapshot, " +
                "tenant_id, user_id, conversation_id, test_run, input_text, file_url, status " +
                "FROM ai_workflow_execution ";
        List<Execution> rows = tenantId == null
                ? jdbcTemplate.query(select +
                                "WHERE execution_id = ? AND tenant_id IS NULL AND user_id = ?",
                        (rs, rowNum) -> mapExecution(rs), executionId, userId)
                : jdbcTemplate.query(select +
                                "WHERE execution_id = ? AND tenant_id = ? AND user_id = ?",
                        (rs, rowNum) -> mapExecution(rs), executionId, tenantId, userId);
        return rows.stream().findFirst();
    }

    public boolean claimQueued(String executionId) {
        return jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'RUNNING', runner_id = ?, " +
                        "lease_until = DATE_ADD(NOW(), INTERVAL ? SECOND), heartbeat_time = NOW(), " +
                        "lock_version = lock_version + 1, update_time = NOW() " +
                        "WHERE execution_id = ? AND status = 'QUEUED' AND runner_id = ?",
                instanceIdentity.id(), leaseSeconds, executionId, instanceIdentity.id()) == 1;
    }

    public boolean markFailed(String executionId, String errorMessage) {
        return jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'FAILED', error_message = ?, " +
                        "runner_id = NULL, lease_until = NULL, heartbeat_time = NULL, " +
                        "lock_version = lock_version + 1, update_time = NOW(), finish_time = NOW() " +
                        "WHERE execution_id = ? AND status IN ('QUEUED','RUNNING') AND runner_id = ?",
                abbreviate(errorMessage, 2000), executionId, instanceIdentity.id()) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markCancelled(String executionId) {
        int updated = jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'CANCELLED', lock_version = lock_version + 1, " +
                        "runner_id = NULL, lease_until = NULL, heartbeat_time = NULL, " +
                        "update_time = NOW(), finish_time = NOW() " +
                "WHERE execution_id = ? AND status IN ('QUEUED','RUNNING','WAITING_APPROVAL')",
                executionId);
        if (updated != 1) {
            return false;
        }
        jdbcTemplate.update(
                "UPDATE ai_workflow_approval SET status = 'CANCELLED', decision = 'cancelled', " +
                        "decision_time = NOW(), lock_version = lock_version + 1, update_time = NOW() " +
                        "WHERE execution_id = ? AND status = 'PENDING'",
                executionId);
        jdbcTemplate.update(
                "UPDATE ai_graph_checkpoint SET status = 'cancelled', update_time = NOW() " +
                "WHERE execution_id = ? ORDER BY sequence_no DESC LIMIT 1",
                executionId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markCancelledIfOwned(String executionId) {
        int updated = jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'CANCELLED', " +
                        "runner_id = NULL, lease_until = NULL, heartbeat_time = NULL, " +
                        "lock_version = lock_version + 1, update_time = NOW(), finish_time = NOW() " +
                        "WHERE execution_id = ? AND runner_id = ? AND status IN ('QUEUED','RUNNING')",
                executionId, instanceIdentity.id());
        if (updated == 1) {
            jdbcTemplate.update(
                    "UPDATE ai_graph_checkpoint SET status = 'cancelled', update_time = NOW() " +
                            "WHERE execution_id = ? ORDER BY sequence_no DESC LIMIT 1",
                    executionId);
        }
        return updated == 1;
    }

    public boolean markSucceeded(String executionId, String resultText) {
        return jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'SUCCEEDED', result_text = ?, " +
                        "runner_id = NULL, lease_until = NULL, heartbeat_time = NULL, " +
                        "lock_version = lock_version + 1, update_time = NOW(), finish_time = NOW() " +
                        "WHERE execution_id = ? AND status = 'RUNNING' AND runner_id = ?",
                resultText, executionId, instanceIdentity.id()) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public String createApproval(String executionId, String nodeId) {
        String approvalId = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'WAITING_APPROVAL', current_node_id = ?, " +
                        "runner_id = NULL, lease_until = NULL, heartbeat_time = NULL, " +
                        "lock_version = lock_version + 1, update_time = NOW() " +
                        "WHERE execution_id = ? AND status = 'RUNNING' AND runner_id = ?",
                nodeId, executionId, instanceIdentity.id());
        if (updated != 1) {
            throw new IllegalStateException("工作流执行状态已变化，无法创建审批任务");
        }
        jdbcTemplate.update(
                "INSERT INTO ai_workflow_approval " +
                        "(approval_id, execution_id, node_instance_id, status, lock_version, create_time, update_time) " +
                        "VALUES (?, ?, ?, 'PENDING', 0, NOW(), NOW())",
                approvalId, executionId, nodeId);
        return approvalId;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean approve(String executionId, String approvalId, Long ownerUserId,
                           Long reviewerUserId, String feedback) {
        int claimed = updateApproval(
                executionId, approvalId, ownerUserId, reviewerUserId, feedback, "APPROVED", "approved");
        if (claimed != 1) {
            return false;
        }
        int resumed = jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'RUNNING', runner_id = ?, " +
                        "lease_until = DATE_ADD(NOW(), INTERVAL ? SECOND), heartbeat_time = NOW(), " +
                        "lock_version = lock_version + 1, update_time = NOW() " +
                        "WHERE execution_id = ? AND status = 'WAITING_APPROVAL'",
                instanceIdentity.id(), leaseSeconds, executionId);
        if (resumed != 1) {
            throw new IllegalStateException("工作流执行状态已变化，审批未生效");
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean reject(String executionId, String approvalId, Long ownerUserId,
                          Long reviewerUserId, String feedback) {
        int claimed = updateApproval(
                executionId, approvalId, ownerUserId, reviewerUserId, feedback, "REJECTED", "rejected");
        if (claimed != 1) {
            return false;
        }
        int rejected = jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET status = 'REJECTED', runner_id = NULL, " +
                        "lease_until = NULL, heartbeat_time = NULL, finish_time = NOW(), " +
                        "lock_version = lock_version + 1, update_time = NOW() " +
                        "WHERE execution_id = ? AND status = 'WAITING_APPROVAL'",
                executionId);
        if (rejected != 1) {
            throw new IllegalStateException("工作流执行状态已变化，审批未生效");
        }
        jdbcTemplate.update(
                "UPDATE ai_graph_checkpoint SET status = 'rejected', update_time = NOW() " +
                        "WHERE execution_id = ? ORDER BY sequence_no DESC LIMIT 1",
                executionId);
        return true;
    }

    public List<Map<String, Object>> listPendingApprovals(Long userId) {
        Long tenantId = currentTenantId();
        String select = "SELECT a.approval_id, a.execution_id, a.node_instance_id, a.create_time, " +
                "e.workflow_code, e.conversation_id FROM ai_workflow_approval a " +
                "JOIN ai_workflow_execution e ON e.execution_id = a.execution_id ";
        String suffix = "AND e.status = 'WAITING_APPROVAL' AND e.test_run = 0 " +
                "AND a.status = 'PENDING' ORDER BY a.id DESC";
        return tenantId == null
                ? jdbcTemplate.queryForList(select +
                                "WHERE e.tenant_id IS NULL AND e.user_id = ? " + suffix,
                        userId)
                : jdbcTemplate.queryForList(select +
                                "WHERE e.tenant_id = ? AND e.user_id = ? " + suffix,
                        tenantId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public long nextEventSequence(String executionId) {
        return reserveEventSequences(executionId, 1).first();
    }

    @Transactional(rollbackFor = Exception.class)
    public EventSequenceBlock reserveEventSequences(String executionId, int blockSize) {
        if (blockSize < 1 || blockSize > 4096) {
            throw new IllegalArgumentException("事件序号预留数量必须在 1 到 4096 之间");
        }
        Long current = jdbcTemplate.queryForObject(
                "SELECT event_sequence FROM ai_workflow_execution WHERE execution_id = ? FOR UPDATE",
                Long.class, executionId);
        if (current == null) {
            throw new IllegalStateException("工作流执行不存在: " + executionId);
        }
        long first = Math.addExact(current, 1L);
        long last = Math.addExact(current, blockSize);
        jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET event_sequence = ? WHERE execution_id = ?",
                last, executionId);
        return new EventSequenceBlock(first, last);
    }

    @Transactional(rollbackFor = Exception.class)
    public long nextCheckpointSequence(String executionId) {
        Long current = jdbcTemplate.queryForObject(
                "SELECT checkpoint_sequence FROM ai_workflow_execution " +
                        "WHERE execution_id = ? FOR UPDATE",
                Long.class, executionId);
        if (current == null) {
            throw new IllegalStateException("工作流执行不存在: " + executionId);
        }
        long next = current + 1;
        jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET checkpoint_sequence = ? WHERE execution_id = ?",
                next, executionId);
        return next;
    }

    public boolean renewLease(String executionId) {
        return jdbcTemplate.update(
                "UPDATE ai_workflow_execution SET lease_until = DATE_ADD(NOW(), INTERVAL ? SECOND), " +
                        "heartbeat_time = NOW() WHERE execution_id = ? AND runner_id = ? " +
                        "AND status IN ('QUEUED','RUNNING') AND lease_until >= NOW()",
                leaseSeconds, executionId, instanceIdentity.id()) == 1;
    }

    public boolean hasActiveLease(String executionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_workflow_execution WHERE execution_id = ? " +
                        "AND status = 'RUNNING' AND runner_id = ? AND lease_until >= NOW()",
                Integer.class, executionId, instanceIdentity.id());
        return count != null && count == 1;
    }

    public boolean shouldStopLocalExecution(String executionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, runner_id FROM ai_workflow_execution WHERE execution_id = ?",
                executionId);
        if (rows.isEmpty()) {
            return true;
        }
        String status = String.valueOf(rows.get(0).get("status"));
        Object runnerValue = rows.get(0).get("runner_id");
        String runnerId = runnerValue == null ? null : runnerValue.toString();
        if (WAITING_APPROVAL.equals(status)) {
            return false;
        }
        return !Set.of(QUEUED, RUNNING).contains(status)
                || !instanceIdentity.id().equals(runnerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int failExpiredLeases() {
        List<String> executionIds = jdbcTemplate.queryForList(
                "SELECT execution_id FROM ai_workflow_execution " +
                        "WHERE status IN ('QUEUED','RUNNING') AND lease_until IS NOT NULL " +
                        "AND lease_until < NOW() LIMIT 500 FOR UPDATE",
                String.class);
        for (String executionId : executionIds) {
            jdbcTemplate.update(
                    "UPDATE ai_workflow_execution SET status = 'FAILED', " +
                            "error_message = '执行实例租约过期，任务已由系统回收', " +
                            "runner_id = NULL, lease_until = NULL, heartbeat_time = NULL, " +
                            "finish_time = NOW(), lock_version = lock_version + 1, update_time = NOW() " +
                            "WHERE execution_id = ? AND status IN ('QUEUED','RUNNING') " +
                            "AND lease_until < NOW()",
                    executionId);
            jdbcTemplate.update(
                    "UPDATE ai_graph_checkpoint SET status = 'error', update_time = NOW() " +
                            "WHERE execution_id = ? ORDER BY sequence_no DESC LIMIT 1",
                    executionId);
        }
        return executionIds.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int expirePendingApprovals(int approvalExpireDays) {
        List<String> executionIds = jdbcTemplate.queryForList(
                "SELECT execution_id FROM ai_workflow_execution WHERE status = 'WAITING_APPROVAL' " +
                        "AND update_time < DATE_SUB(NOW(), INTERVAL ? DAY) " +
                        "LIMIT 500 FOR UPDATE",
                String.class, approvalExpireDays);
        if (executionIds.isEmpty()) {
            return 0;
        }
        for (String executionId : executionIds) {
            markCancelled(executionId);
        }
        return executionIds.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int cleanupTerminalExecutions(int retentionDays) {
        List<String> executionIds = jdbcTemplate.queryForList(
                "SELECT execution_id FROM ai_workflow_execution " +
                        "WHERE status IN ('SUCCEEDED','FAILED','CANCELLED','REJECTED') " +
                        "AND finish_time IS NOT NULL AND finish_time < DATE_SUB(NOW(), INTERVAL ? DAY) " +
                        "LIMIT 500",
                String.class, retentionDays);
        for (String executionId : executionIds) {
            jdbcTemplate.update("DELETE FROM ai_workflow_approval WHERE execution_id = ?", executionId);
            jdbcTemplate.update("DELETE FROM ai_graph_checkpoint WHERE execution_id = ?", executionId);
            jdbcTemplate.update("DELETE FROM ai_workflow_execution WHERE execution_id = ?", executionId);
        }
        return executionIds.size();
    }

    private int updateApproval(String executionId, String approvalId, Long ownerUserId,
                               Long reviewerUserId, String feedback, String status, String decision) {
        return jdbcTemplate.update(
                "UPDATE ai_workflow_approval a " +
                        "JOIN ai_workflow_execution e ON e.execution_id = a.execution_id " +
                        "SET a.status = ?, a.decision = ?, a.feedback = ?, a.reviewer_user_id = ?, " +
                        "a.decision_time = NOW(), a.lock_version = a.lock_version + 1, a.update_time = NOW() " +
                        "WHERE a.approval_id = ? AND a.execution_id = ? AND a.status = 'PENDING' AND e.user_id = ?",
                status, decision, feedback, reviewerUserId, approvalId, executionId, ownerUserId);
    }

    private Execution mapExecution(ResultSet rs) throws SQLException {
        return new Execution(
                rs.getString("execution_id"),
                rs.getString("workflow_code"),
                rs.getInt("workflow_version"),
                rs.getString("workflow_snapshot"),
                rs.getObject("tenant_id", Long.class),
                rs.getLong("user_id"),
                rs.getObject("conversation_id", Long.class),
                rs.getBoolean("test_run"),
                rs.getString("input_text"),
                rs.getString("file_url"),
                rs.getString("status"));
    }

    private Long currentTenantId() {
        if (!CallerUtils.isPlatformMode()) {
            return null;
        }
        String tenantId = CallerUtils.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("中台模式缺少租户ID");
        }
        return Long.valueOf(tenantId);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
