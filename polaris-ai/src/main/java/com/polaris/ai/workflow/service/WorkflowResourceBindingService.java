package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.WorkflowResourceBindingApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowResourceBindingCommand;
import com.polaris.ai.workflow.application.WorkflowResourceBindingView;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowDefinition;
import com.polaris.ai.workflow.domain.WorkflowResourceBinding;
import com.polaris.ai.workflow.mapper.WorkflowDefinitionMapper;
import com.polaris.ai.workflow.mapper.WorkflowResourceBindingMapper;
import com.polaris.ai.workflow.registry.WorkflowResourceRegistry;
import com.polaris.ai.workflow.spi.WorkflowResourceProvider;
import com.polaris.ai.workflow.spi.WorkflowResourceRequest;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 不可变定义所用逻辑资源标识的所有者安全增删改查服务。 */
@Service
public class WorkflowResourceBindingService
        implements WorkflowResourceBindingApplicationFacade {

    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "PROD");
    private static final Set<String> SCOPE_TYPES = Set.of("OWNER", "WORKFLOW");

    private final WorkflowResourceBindingMapper bindingMapper;
    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowResourceRegistry resourceRegistry;
    private final WorkflowProperties properties;

    public WorkflowResourceBindingService(
            WorkflowResourceBindingMapper bindingMapper,
            WorkflowDefinitionMapper definitionMapper,
            WorkflowResourceRegistry resourceRegistry,
            WorkflowProperties properties) {
        this.bindingMapper = bindingMapper;
        this.definitionMapper = definitionMapper;
        this.resourceRegistry = resourceRegistry;
        this.properties = properties;
    }

    @Override
    public List<WorkflowResourceBindingView> list(Long definitionId, String environment) {
        requireEnabled();
        OwnerScope scope = currentScope();
        if (definitionId != null) {
            requireDefinition(definitionId, scope);
        }
        LambdaQueryWrapper<WorkflowResourceBinding> query =
                new LambdaQueryWrapper<WorkflowResourceBinding>()
                        .eq(WorkflowResourceBinding::getOwnerType, scope.ownerType())
                        .eq(WorkflowResourceBinding::getOwnerId, scope.ownerId());
        if (definitionId != null) {
            query.and(wrapper -> wrapper
                    .and(shared -> shared
                            .eq(WorkflowResourceBinding::getScopeType, "OWNER")
                            .eq(WorkflowResourceBinding::getScopeId, scope.ownerId()))
                    .or(workflow -> workflow
                            .eq(WorkflowResourceBinding::getScopeType, "WORKFLOW")
                            .eq(WorkflowResourceBinding::getScopeId, definitionId)));
        }
        if (environment != null && !environment.isBlank()) {
            query.eq(WorkflowResourceBinding::getEnvironment,
                    normalizeEnvironment(environment));
        }
        query.orderByAsc(WorkflowResourceBinding::getEnvironment)
                .orderByDesc(WorkflowResourceBinding::getScopeType)
                .orderByAsc(WorkflowResourceBinding::getResourceKind)
                .orderByAsc(WorkflowResourceBinding::getResourceKey);
        return bindingMapper.selectList(query).stream().map(this::view).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResourceBindingView save(WorkflowResourceBindingCommand command) {
        requireEnabled();
        if (command == null) throw new ServiceException("资源绑定请求不能为空");
        OwnerScope scope = currentScope();
        String environment = normalizeEnvironment(command.environment());
        String kind = normalizeKind(command.resourceKind());
        String key = normalizeKey(command.resourceKey());
        String resourceId = normalizeResourceId(command.resourceId());
        String scopeType = normalizeScopeType(command.scopeType(), command.definitionId());
        Long scopeId = scopeId(scopeType, command.definitionId(), scope);
        validateResource(scope.tenantId(), environment, kind, key, resourceId);
        if (command.id() == null) {
            WorkflowResourceBinding duplicate = bindingMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowResourceBinding>()
                            .eq(WorkflowResourceBinding::getOwnerType, scope.ownerType())
                            .eq(WorkflowResourceBinding::getOwnerId, scope.ownerId())
                            .eq(WorkflowResourceBinding::getScopeType, scopeType)
                            .eq(WorkflowResourceBinding::getScopeId, scopeId)
                            .eq(WorkflowResourceBinding::getEnvironment, environment)
                            .eq(WorkflowResourceBinding::getResourceKind, kind)
                            .eq(WorkflowResourceBinding::getResourceKey, key)
                            .last("LIMIT 1"));
            if (duplicate != null) {
                throw new ServiceException("同一环境、类型和逻辑键的资源绑定已存在");
            }
            WorkflowResourceBinding binding = new WorkflowResourceBinding();
            binding.setOwnerType(scope.ownerType());
            binding.setOwnerId(scope.ownerId());
            binding.setTenantId(scope.tenantId());
            binding.setScopeType(scopeType);
            binding.setScopeId(scopeId);
            binding.setEnvironment(environment);
            binding.setResourceKind(kind);
            binding.setResourceKey(key);
            binding.setResourceId(resourceId);
            binding.setBindingVersion(1);
            binding.setStatus("ACTIVE");
            binding.setLockVersion(0);
            binding.setCreateBy(CallerUtils.getUsername());
            binding.setUpdateBy(CallerUtils.getUsername());
            if (bindingMapper.insert(binding) != 1) {
                throw new ServiceException("创建资源绑定失败");
            }
            return view(binding);
        }
        WorkflowResourceBinding existing = requireBinding(command.id(), scope);
        if (command.expectedLockVersion() == null
                || !command.expectedLockVersion().equals(existing.getLockVersion())) {
            throw new ServiceException("资源绑定已被其他用户修改，请刷新后重试");
        }
        existing.setEnvironment(environment);
        existing.setScopeType(scopeType);
        existing.setScopeId(scopeId);
        existing.setResourceKind(kind);
        existing.setResourceKey(key);
        existing.setResourceId(resourceId);
        existing.setBindingVersion(existing.getBindingVersion() + 1);
        existing.setStatus("ACTIVE");
        existing.setUpdateBy(CallerUtils.getUsername());
        if (bindingMapper.updateById(existing) != 1) {
            throw new ServiceException("资源绑定更新冲突，请刷新后重试");
        }
        return view(bindingMapper.selectById(existing.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResourceBindingView disable(Long bindingId) {
        requireEnabled();
        OwnerScope scope = currentScope();
        WorkflowResourceBinding binding = requireBinding(bindingId, scope);
        binding.setStatus("DISABLED");
        binding.setBindingVersion(binding.getBindingVersion() + 1);
        binding.setUpdateBy(CallerUtils.getUsername());
        if (bindingMapper.updateById(binding) != 1) {
            throw new ServiceException("停用资源绑定失败");
        }
        return view(bindingMapper.selectById(bindingId));
    }

    private void validateResource(
            Long tenantId, String environment, String kind, String key, String resourceId) {
        WorkflowResourceProvider provider = resourceRegistry.find(kind)
                .orElseThrow(() -> new ServiceException("资源类型尚未注册: " + kind));
        WorkflowResourceRequest request = new WorkflowResourceRequest(
                tenantId, environment, kind, key, resourceId,
                principalType(), String.valueOf(CallerUtils.getUserId()));
        List<String> errors = provider.validate(request);
        if (errors != null && !errors.isEmpty()) {
            throw new ServiceException("资源绑定校验失败: " + String.join("; ", errors));
        }
    }

    private WorkflowResourceBinding requireBinding(Long id, OwnerScope scope) {
        if (id == null) throw new ServiceException("资源绑定ID不能为空");
        WorkflowResourceBinding binding = bindingMapper.selectById(id);
        if (binding == null
                || !scope.ownerType().equals(binding.getOwnerType())
                || !scope.ownerId().equals(binding.getOwnerId())) {
            throw new ServiceException("资源绑定不存在或无权访问");
        }
        return binding;
    }

    private WorkflowDefinition requireDefinition(Long definitionId, OwnerScope scope) {
        WorkflowDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null || !"0".equals(definition.getDelFlag())
                || !java.util.Objects.equals(definition.getTenantId(), scope.tenantId())) {
            throw new ServiceException("工作流不存在或无权访问");
        }
        return definition;
    }

    private OwnerScope currentScope() {
        if (CallerUtils.isPlatformMode()) {
            Long current = parseTenantId(CallerUtils.getTenantId());
            return new OwnerScope("TENANT", current, current);
        }
        return new OwnerScope("SYSTEM", 0L, null);
    }

    private Long parseTenantId(String value) {
        try {
            long tenantId = Long.parseLong(value);
            if (tenantId <= 0) throw new NumberFormatException();
            return tenantId;
        } catch (Exception e) {
            throw new ServiceException("租户ID格式错误");
        }
    }

    private String normalizeEnvironment(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(result)) {
            throw new ServiceException("资源环境只能是DEV、TEST或PROD");
        }
        return result;
    }

    private String normalizeKind(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new ServiceException("资源类型格式无效");
        }
        return result;
    }

    private String normalizeScopeType(String value, Long definitionId) {
        String result = value == null || value.isBlank()
                ? (definitionId == null ? "OWNER" : "WORKFLOW")
                : value.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(result)) {
            throw new ServiceException("资源绑定作用域只能是 OWNER 或 WORKFLOW");
        }
        return result;
    }

    private Long scopeId(String scopeType, Long definitionId, OwnerScope ownerScope) {
        if ("OWNER".equals(scopeType)) {
            return ownerScope.ownerId();
        }
        if (definitionId == null) {
            throw new ServiceException("工作流级资源绑定必须提供工作流 ID");
        }
        requireDefinition(definitionId, ownerScope);
        return definitionId;
    }

    private String normalizeKey(String value) {
        String result = value == null ? "" : value.trim();
        if (!result.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            throw new ServiceException("资源逻辑键格式无效");
        }
        return result;
    }

    private String normalizeResourceId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 128) {
            throw new ServiceException("资源ID不能为空且不能超过128个字符");
        }
        return result;
    }

    private String principalType() {
        CallerContext caller = CallerUtils.getContext();
        if (CallerUtils.getUsername().startsWith("apikey:")) return "API_KEY";
        return caller.isPlatformMode() ? "PLATFORM_USER" : "ADMIN";
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ServiceException("工作流 尚未启用");
        }
    }

    private WorkflowResourceBindingView view(WorkflowResourceBinding binding) {
        return new WorkflowResourceBindingView(
                binding.getId(), binding.getOwnerType(), binding.getOwnerId(),
                binding.getTenantId(), binding.getScopeType(), binding.getScopeId(),
                "WORKFLOW".equals(binding.getScopeType()) ? binding.getScopeId() : null,
                binding.getEnvironment(),
                binding.getResourceKind(), binding.getResourceKey(), binding.getResourceId(),
                binding.getBindingVersion(), binding.getStatus(), binding.getLockVersion(),
                binding.getCreateTime(), binding.getUpdateTime());
    }

    private record OwnerScope(String ownerType, Long ownerId, Long tenantId) {
    }
}
