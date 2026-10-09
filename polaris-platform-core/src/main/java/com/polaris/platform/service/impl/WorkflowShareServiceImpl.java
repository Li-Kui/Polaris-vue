package com.polaris.platform.service.impl;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.workflow.application.WorkflowDefinitionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowExecutionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowShareDefaultsCommand;
import com.polaris.ai.workflow.application.WorkflowShareDefinitionView;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.auth.WorkflowShareOriginPolicy;
import com.polaris.platform.auth.WorkflowShareRateLimiter;
import com.polaris.platform.domain.WorkflowShare;
import com.polaris.platform.mapper.WorkflowShareMapper;
import com.polaris.platform.service.IWorkflowShareService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 工作流分享服务实现
 *
 * @author polaris
 */
@Service
public class WorkflowShareServiceImpl implements IWorkflowShareService {

    private static final Set<String> PAGE_TYPES = Set.of(
            "form", "chat", "report", "task", "query", "image", "compare", "gallery");

    @Autowired
    private WorkflowShareMapper shareMapper;

    @Autowired
    private WorkflowExecutionApplicationFacade workflowFacade;

    @Autowired
    private WorkflowDefinitionApplicationFacade definitionFacade;

    @Autowired
    private WorkflowShareRateLimiter rateLimiter;

    @Override
    public List<WorkflowShare> selectShareListByTenantId(Long tenantId) {
        Long currentTenantId = tenantId != null ? tenantId : PlatformTenantGuard.requireTenantId();
        if (!PlatformTenantGuard.belongsToCurrentTenant(currentTenantId)) {
            return List.of();
        }
        return shareMapper.selectByTenantId(currentTenantId);
    }

    @Override
    public WorkflowShare selectShareById(Long id) {
        WorkflowShare share = shareMapper.selectById(id);
        return share != null && PlatformTenantGuard.belongsToCurrentTenant(share.getTenantId())
                ? share : null;
    }

    @Override
    public WorkflowShare selectShareByCode(String shareCode) {
        return shareMapper.selectByShareCode(shareCode);
    }

    @Override
    public WorkflowShare createShare(WorkflowShare share) {
        if (share == null || share.getWorkflowDefinitionId() == null) {
            throw new ServiceException("工作流定义ID不能为空");
        }
        WorkflowShareDefinitionView definition = workflowFacade.getShareDefinition(
                share.getWorkflowDefinitionId());
        share.setTenantId(PlatformTenantGuard.requireTenantId());
        if (!share.getTenantId().equals(definition.tenantId())) {
            throw new ServiceException("工作流不存在、未发布或无权访问", 404);
        }
        if (share.getPageType() == null || share.getPageType().isBlank()) {
            // 保留空覆盖，运行时继承工作流默认类型。
            share.setPageType(null);
        }
        normalize(share, true);
        if (share.getStatus() == null) {
            share.setStatus("0");
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            share.setShareCode(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            try {
                shareMapper.insert(share);
                return share;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) throw e;
            }
        }
        throw new ServiceException("生成分享码失败，请重试");
    }

    @Override
    public int updateShare(WorkflowShare share) {
        if (share == null || share.getId() == null) {
            throw new ServiceException("分享ID不能为空", 400);
        }
        WorkflowShare existing = selectShareById(share.getId());
        if (existing == null) {
            throw new ServiceException("分享不存在或无权访问", 404);
        }
        normalize(share, false);
        share.setTenantId(existing.getTenantId());
        return shareMapper.update(share);
    }

    @Override
    public WorkflowShareDefinitionView getShareDefinition(Long workflowDefinitionId) {
        PlatformTenantGuard.requireTenantId();
        return workflowFacade.getShareDefinition(workflowDefinitionId);
    }

    @Override
    public WorkflowShareDefinitionView updateShareDefaults(Long workflowDefinitionId, WorkflowShareDefaultsCommand command) {
        // 必须先验证已发布状态和租户归属，再写入；不修改草稿、版本或分享访问设置。
        getShareDefinition(workflowDefinitionId);
        definitionFacade.updateShareDefaults(workflowDefinitionId, command);
        return getShareDefinition(workflowDefinitionId);
    }

    @Override
    public int deleteShare(Long id) {
        if (selectShareById(id) == null) {
            throw new ServiceException("分享不存在或无权访问", 404);
        }
        return shareMapper.deleteById(id);
    }

    @Override
    public List<WorkflowShare> selectShareListByWorkflowDefinitionId(Long workflowDefinitionId) {
        Long tenantId = PlatformTenantGuard.requireTenantId();
        return shareMapper.selectByWorkflowDefinitionId(tenantId, workflowDefinitionId);
    }

    @Override
    public WorkflowShare validateAndGet(String shareCode) {
        return validateAndGet(shareCode, WorkflowShareRateLimiter.RequestType.READ);
    }

    @Override
    public WorkflowShare validateAndGet(String shareCode, WorkflowShareRateLimiter.RequestType requestType) {
        WorkflowShare share = shareMapper.selectByShareCode(shareCode);
        if (share == null) {
            throw new ServiceException("分享不存在或已失效", 404);
        }
        if (!"0".equals(share.getStatus())) {
            throw new ServiceException("该分享已被停用", 403);
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            throw new ServiceException("该分享已过期", 403);
        }
        try {
            if (!rateLimiter.tryAcquire(share.getId(), share.getRateLimit(), requestType)) {
                String message = switch (requestType) {
                    case EXECUTION -> "分享运行次数已达到每分钟上限，请稍后重试";
                    case UPLOAD -> "分享上传过于频繁，请稍后重试";
                    case READ -> "分享访问过于频繁，请稍后重试";
                };
                throw new ServiceException(message, 429);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("限流服务暂不可用，请稍后重试", 503);
        }
        return share;
    }

    private void normalize(WorkflowShare share, boolean creating) {
        if (share.getPageType() != null && share.getPageType().isBlank()) {
            share.setPageType(null);
        }
        if (creating && (share.getShareName() == null || share.getShareName().isBlank())) {
            throw new ServiceException("分享名称不能为空");
        }
        if (share.getShareName() != null) {
            String name = share.getShareName().trim();
            if (name.isEmpty() || name.length() > 128) {
                throw new ServiceException("分享名称不能为空且不能超过128个字符");
            }
            share.setShareName(name);
        }
        if (share.getPageType() != null && !PAGE_TYPES.contains(share.getPageType())) {
            throw new ServiceException("页面类型无效");
        }
        if (share.getRateLimit() != null
                && (share.getRateLimit() <= 0 || share.getRateLimit() > 10000)) {
            throw new ServiceException("每分钟运行次数必须在1到10000之间");
        }
        if (share.getStatus() != null && !Set.of("0", "1").contains(share.getStatus())) {
            throw new ServiceException("分享状态无效");
        }
        validateJsonObject(share.getPageConfigJson(), "页面配置");
        validateOrigins(share.getAllowedOrigins());
    }

    private void validateJsonObject(String value, String fieldName) {
        if (value == null || value.isBlank()) return;
        try {
            JSON.parseObject(value);
        } catch (Exception e) {
            throw new ServiceException(fieldName + "格式无效");
        }
    }

    private void validateOrigins(String value) {
        if (value == null || value.isBlank()) return;
        try {
            List<String> origins = JSON.parseArray(value, String.class);
            if (origins.stream().anyMatch(origin -> !WorkflowShareOriginPolicy.isSafeOrigin(origin))) {
                throw new ServiceException("iframe 来源必须是完整的 HTTP(S) Origin");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("iframe 来源格式无效");
        }
    }
}
