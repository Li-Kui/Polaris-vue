package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.workflow.domain.WorkflowResourceBinding;
import com.polaris.ai.workflow.mapper.WorkflowResourceBindingMapper;
import com.polaris.ai.workflow.spi.WorkflowResourceUsageInspector;
import org.springframework.stereotype.Service;

/**
 * 工作流资源引用统计服务。
 */
@Service
public class WorkflowResourceUsageService implements WorkflowResourceUsageInspector {

    private final WorkflowResourceBindingMapper bindingMapper;

    public WorkflowResourceUsageService(WorkflowResourceBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    @Override
    public long countActiveUsages(Long tenantId, String resourceKind, String resourceId) {
        LambdaQueryWrapper<WorkflowResourceBinding> query =
                new LambdaQueryWrapper<WorkflowResourceBinding>()
                        .eq(WorkflowResourceBinding::getResourceKind, resourceKind)
                        .eq(WorkflowResourceBinding::getResourceId, resourceId)
                        .eq(WorkflowResourceBinding::getStatus, "ACTIVE");
        if (tenantId == null) {
            query.isNull(WorkflowResourceBinding::getTenantId);
        } else {
            query.eq(WorkflowResourceBinding::getTenantId, tenantId);
        }
        return bindingMapper.selectCount(query);
    }
}
