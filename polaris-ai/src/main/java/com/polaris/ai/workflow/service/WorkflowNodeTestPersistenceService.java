package com.polaris.ai.workflow.service;

import com.polaris.ai.workflow.domain.WorkflowNodeTestAudit;
import com.polaris.ai.workflow.domain.WorkflowNodeTestRun;
import com.polaris.ai.workflow.mapper.WorkflowNodeTestAuditMapper;
import com.polaris.ai.workflow.mapper.WorkflowNodeTestRunMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 保证试运行状态和无样本审计摘要在同一事务内变化。 */
@Service
public class WorkflowNodeTestPersistenceService {

    private final WorkflowNodeTestRunMapper testRunMapper;
    private final WorkflowNodeTestAuditMapper auditMapper;

    public WorkflowNodeTestPersistenceService(
            WorkflowNodeTestRunMapper testRunMapper,
            WorkflowNodeTestAuditMapper auditMapper) {
        this.testRunMapper = testRunMapper;
        this.auditMapper = auditMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(WorkflowNodeTestRun run, WorkflowNodeTestAudit audit) {
        if (testRunMapper.insert(run) != 1 || auditMapper.insert(audit) != 1) {
            throw new ServiceException("创建单节点试运行任务失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int cancel(String testRunId) {
        int updated = testRunMapper.requestCancel(testRunId);
        if (updated == 1 && auditMapper.cancel(testRunId) != 1) {
            throw new ServiceException("记录单节点试运行取消审计失败");
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public int finish(
            String testRunId, String runnerId, long fencingToken,
            String status, String inputJson, String outputJson, String usageJson,
            String schemaSource, String schemaSourceVersion,
            String schemaDiagnosticsJson, String errorCode,
            String errorMessage, long durationMs, Long totalTokens,
            BigDecimal costAmount, int attemptCount) {
        int updated = testRunMapper.finish(
                testRunId, runnerId, fencingToken, status, inputJson, outputJson, usageJson,
                schemaSource, schemaSourceVersion, schemaDiagnosticsJson,
                errorCode, errorMessage, durationMs);
        if (updated == 1 && auditMapper.finish(
                testRunId, status, usageJson, totalTokens, costAmount,
                attemptCount, errorCode, durationMs) != 1) {
            throw new ServiceException("记录单节点试运行终态审计失败");
        }
        return updated;
    }
}
