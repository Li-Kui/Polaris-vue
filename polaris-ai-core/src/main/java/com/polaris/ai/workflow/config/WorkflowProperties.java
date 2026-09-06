package com.polaris.ai.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 工作流启用范围及高风险能力开关。 */
@Component
@ConfigurationProperties(prefix = "ai.workflow")
public class WorkflowProperties {

    private boolean enabled;
    private boolean writeNodesEnabled;
    private boolean codeNodeEnabled;
    private boolean agentReadOnlyToolsEnabled;
    private boolean workerEnabled = true;
    private boolean acceptingNewExecutions = true;
    private int workerBatchSize = 10;
    private int leaseSeconds = 30;
    private int tenantConcurrency = 20;
    private int workflowConcurrency = 10;
    private int principalConcurrency = 5;
    private int agentMaxToolCalls = 5;
    private int agentMaxToolResultChars = 20000;
    private Set<Long> allowedTenantIds = new LinkedHashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWriteNodesEnabled() {
        return writeNodesEnabled;
    }

    public void setWriteNodesEnabled(boolean writeNodesEnabled) {
        this.writeNodesEnabled = writeNodesEnabled;
    }

    public boolean isCodeNodeEnabled() {
        return codeNodeEnabled;
    }

    public boolean isAgentReadOnlyToolsEnabled() {
        return agentReadOnlyToolsEnabled;
    }

    public void setAgentReadOnlyToolsEnabled(boolean agentReadOnlyToolsEnabled) {
        this.agentReadOnlyToolsEnabled = agentReadOnlyToolsEnabled;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public boolean isAcceptingNewExecutions() {
        return acceptingNewExecutions;
    }

    public void setAcceptingNewExecutions(boolean acceptingNewExecutions) {
        this.acceptingNewExecutions = acceptingNewExecutions;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public int getWorkerBatchSize() {
        return Math.max(1, Math.min(workerBatchSize, 100));
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }

    public int getLeaseSeconds() {
        return Math.max(10, Math.min(leaseSeconds, 300));
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public int getTenantConcurrency() {
        return Math.max(1, Math.min(tenantConcurrency, 10000));
    }

    public void setTenantConcurrency(int tenantConcurrency) {
        this.tenantConcurrency = tenantConcurrency;
    }

    public int getWorkflowConcurrency() {
        return Math.max(1, Math.min(workflowConcurrency, 10000));
    }

    public void setWorkflowConcurrency(int workflowConcurrency) {
        this.workflowConcurrency = workflowConcurrency;
    }

    public int getPrincipalConcurrency() {
        return Math.max(1, Math.min(principalConcurrency, 10000));
    }

    public void setPrincipalConcurrency(int principalConcurrency) {
        this.principalConcurrency = principalConcurrency;
    }

    public int getAgentMaxToolCalls() {
        return Math.max(1, Math.min(agentMaxToolCalls, 20));
    }

    public void setAgentMaxToolCalls(int agentMaxToolCalls) {
        this.agentMaxToolCalls = agentMaxToolCalls;
    }

    public int getAgentMaxToolResultChars() {
        return Math.max(1000, Math.min(agentMaxToolResultChars, 100000));
    }

    public void setAgentMaxToolResultChars(int agentMaxToolResultChars) {
        this.agentMaxToolResultChars = agentMaxToolResultChars;
    }

    public void setCodeNodeEnabled(boolean codeNodeEnabled) {
        this.codeNodeEnabled = codeNodeEnabled;
    }

    public Set<Long> getAllowedTenantIds() {
        return allowedTenantIds;
    }

    public void setAllowedTenantIds(Set<Long> allowedTenantIds) {
        this.allowedTenantIds = allowedTenantIds == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedTenantIds);
    }

    public boolean isEnabledForTenant(Long tenantId) {
        if (!enabled) {
            return false;
        }
        return allowedTenantIds.isEmpty() || tenantId != null && allowedTenantIds.contains(tenantId);
    }
}
