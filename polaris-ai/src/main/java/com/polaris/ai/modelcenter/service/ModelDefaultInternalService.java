package com.polaris.ai.modelcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.domain.AiModelDefault;
import com.polaris.ai.mapper.AiModelDefaultMapper;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/** 默认模型的内部精确查询服务，不负责扩大模型可见范围。 */
@Service
@Transactional(readOnly = true)
public class ModelDefaultInternalService {

    private final AiModelDefaultMapper defaultMapper;
    private final ModelAggregateAccessGuard accessGuard;
    private final ModelDefinitionResolver definitionResolver;

    public ModelDefaultInternalService(
            AiModelDefaultMapper defaultMapper,
            ModelAggregateAccessGuard accessGuard,
            ModelDefinitionResolver definitionResolver) {
        this.defaultMapper = defaultMapper;
        this.accessGuard = accessGuard;
        this.definitionResolver = definitionResolver;
    }

    public AiModelDefault find(
            String scopeType, Long scopeId, String capabilityCode) {
        AiModelDefault modelDefault = defaultMapper.selectOne(
                new LambdaQueryWrapper<AiModelDefault>()
                .eq(AiModelDefault::getScopeType,
                        Objects.requireNonNull(scopeType, "scopeType"))
                .eq(AiModelDefault::getScopeId,
                        Objects.requireNonNull(scopeId, "scopeId"))
                .eq(AiModelDefault::getCapabilityCode,
                        Objects.requireNonNull(capabilityCode, "capabilityCode")));
        if (modelDefault != null) {
            accessGuard.requireAccessible(modelDefault.getModelConfigId());
        }
        return modelDefault;
    }

    /** 按当前调用身份解析默认模型；默认记录本身不授予模型访问权限。 */
    public Long resolveModelId(String capabilityCode) {
        String capability = normalizeCapability(capabilityCode);
        AiModelDefault resolved = null;
        if (CallerUtils.isPlatformMode()) {
            resolved = find("TENANT", requireTenantId(), capability);
        } else if (CallerUtils.getDeptId() != null) {
            resolved = find("DEPT", CallerUtils.getDeptId(), capability);
        }
        if (resolved == null && !CallerUtils.isPlatformMode()
                && (CallerUtils.isSuperAdmin()
                || CallerUtils.getDeptId() == null)) {
            resolved = find("GLOBAL", 0L, capability);
        }
        if (resolved == null) {
            throw new ServiceException(
                    "DEFAULT_MODEL_NOT_CONFIGURED: " + capability);
        }
        definitionResolver.resolve(resolved.getModelConfigId(), capability);
        return resolved.getModelConfigId();
    }

    /** 兼容旧“设为默认”按钮，但单一真相源只写 ai_model_default。 */
    @Transactional(rollbackFor = Exception.class)
    public void setForCurrentScope(
            String capabilityCode,
            Long modelConfigId) {
        String capability = normalizeCapability(capabilityCode);
        accessGuard.requireAccessible(modelConfigId);
        definitionResolver.resolve(modelConfigId, capability);
        Scope scope = currentWritableScope();
        AiModelDefault existing = defaultMapper.selectOne(
                new LambdaQueryWrapper<AiModelDefault>()
                        .eq(AiModelDefault::getScopeType, scope.type())
                        .eq(AiModelDefault::getScopeId, scope.id())
                        .eq(AiModelDefault::getCapabilityCode, capability));
        Date now = new Date();
        if (existing == null) {
            AiModelDefault created = new AiModelDefault();
            created.setScopeType(scope.type());
            created.setScopeId(scope.id());
            created.setCapabilityCode(capability);
            created.setModelConfigId(modelConfigId);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            if (defaultMapper.insert(created) != 1) {
                throw new ServiceException("DEFAULT_MODEL_SAVE_FAILED");
            }
            return;
        }
        existing.setModelConfigId(modelConfigId);
        existing.setUpdateTime(now);
        if (defaultMapper.updateById(existing) != 1) {
            throw new ServiceException("DEFAULT_MODEL_SAVE_FAILED");
        }
    }

    private Scope currentWritableScope() {
        if (CallerUtils.isPlatformMode()) {
            return new Scope("TENANT", requireTenantId());
        }
        if (CallerUtils.getDeptId() != null && !CallerUtils.isSuperAdmin()) {
            return new Scope("DEPT", CallerUtils.getDeptId());
        }
        if (!CallerUtils.isSuperAdmin()) {
            throw new ServiceException("GLOBAL_DEFAULT_REQUIRES_ADMIN");
        }
        return new Scope("GLOBAL", 0L);
    }

    private Long requireTenantId() {
        try {
            long value = Long.parseLong(CallerUtils.getTenantId());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (Exception e) {
            throw new ServiceException("TENANT_CONTEXT_REQUIRED");
        }
    }

    private String normalizeCapability(String value) {
        String result = value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new ServiceException("CAPABILITY_CODE_INVALID");
        }
        return result;
    }

    private record Scope(String type, Long id) {
    }
}
