package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowConcurrencyQuota;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.mapper.WorkflowConcurrencyQuotaMapper;
import com.polaris.ai.workflow.mapper.WorkflowExecutionMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 无先计数后插入竞态地预留租户、工作流和调用主体并发配额。 */
@Component
public class WorkflowQuotaService {

    private final WorkflowConcurrencyQuotaMapper quotaMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public WorkflowQuotaService(
            WorkflowConcurrencyQuotaMapper quotaMapper,
            WorkflowExecutionMapper executionMapper,
            WorkflowProperties properties,
            ObjectMapper objectMapper) {
        this.quotaMapper = quotaMapper;
        this.executionMapper = executionMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void reserve(WorkflowExecution execution) {
        List<Scope> scopes = scopes(execution);
        scopes.sort(Comparator.comparing(Scope::key));
        for (Scope scope : scopes) {
            quotaMapper.insertIfAbsent(
                    execution.getTenantId(), scope.key(), scope.type(), scope.maximum());
            WorkflowConcurrencyQuota current = quotaMapper.selectByScopeForUpdate(scope.key());
            if (current == null || quotaMapper.reserve(scope.key(), scope.maximum()) != 1) {
                throw new ServiceException(scope.type() + "并发执行数已达到上限");
            }
        }
        execution.setQuotaScopesJson(writeKeys(scopes));
        execution.setQuotaReleased(false);
    }

    public void release(WorkflowExecution execution) {
        if (execution == null || Boolean.TRUE.equals(execution.getQuotaReleased())) {
            return;
        }
        if (executionMapper.markQuotaReleased(execution.getExecutionId()) != 1) {
            execution.setQuotaReleased(true);
            return;
        }
        for (String key : readKeys(execution.getQuotaScopesJson())) {
            quotaMapper.release(key);
        }
        execution.setQuotaReleased(true);
    }

    private List<Scope> scopes(WorkflowExecution execution) {
        String tenant = execution.getTenantId() == null
                ? "system" : "tenant:" + execution.getTenantId();
        List<Scope> result = new ArrayList<>();
        result.add(new Scope(tenant, "TENANT", properties.getTenantConcurrency()));
        result.add(new Scope(tenant + ":workflow:" + execution.getDefinitionId(),
                "WORKFLOW", properties.getWorkflowConcurrency()));
        result.add(new Scope(tenant + ":principal:" + execution.getPrincipalType()
                + ":" + execution.getPrincipalId(),
                "PRINCIPAL", properties.getPrincipalConcurrency()));
        return result;
    }

    private String writeKeys(List<Scope> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes.stream().map(Scope::key).toList());
        } catch (Exception e) {
            throw new ServiceException("工作流并发配额快照序列化失败");
        }
    }

    private List<String> readKeys(String value) {
        try {
            return value == null || value.isBlank() ? List.of()
                    : objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new ServiceException("工作流并发配额快照格式无效");
        }
    }

    private record Scope(String key, String type, int maximum) {
    }
}
