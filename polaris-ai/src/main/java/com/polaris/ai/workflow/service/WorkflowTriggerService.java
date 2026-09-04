package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.domain.WorkflowDefinition;
import com.polaris.ai.workflow.domain.WorkflowTrigger;
import com.polaris.ai.workflow.domain.WorkflowVersion;
import com.polaris.ai.workflow.mapper.WorkflowDefinitionMapper;
import com.polaris.ai.workflow.mapper.WorkflowTriggerMapper;
import com.polaris.ai.workflow.mapper.WorkflowVersionMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/** 经过校验的触发器管理服务；凭据始终保留在资源绑定内部。 */
@Service
public class WorkflowTriggerService implements WorkflowTriggerApplicationFacade {

    private static final Set<String> TYPES = Set.of("SCHEDULE", "WEBHOOK", "EVENT");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> SECRET_KEYS = Set.of(
            "secret", "token", "password", "apikey", "api_key", "credential");

    private final WorkflowTriggerMapper triggerMapper;
    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final WorkflowExecutionApplicationFacade executionFacade;

    public WorkflowTriggerService(
            WorkflowTriggerMapper triggerMapper,
            WorkflowDefinitionMapper definitionMapper,
            WorkflowVersionMapper versionMapper,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            WorkflowExecutionApplicationFacade executionFacade) {
        this.triggerMapper = triggerMapper;
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executionFacade = executionFacade;
    }

    @Override
    public List<WorkflowTriggerView> list(Long definitionId) {
        requireEnabled();
        LambdaQueryWrapper<WorkflowTrigger> query = new LambdaQueryWrapper<>();
        applyTenantScope(query, currentTenantId());
        if (definitionId != null) query.eq(WorkflowTrigger::getDefinitionId, definitionId);
        query.orderByDesc(WorkflowTrigger::getCreateTime);
        return triggerMapper.selectList(query).stream().map(this::view).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTriggerView create(WorkflowTriggerCommand command) {
        requireEnabled();
        if (command == null || command.definitionId() == null
                || command.workflowVersionId() == null || command.triggerType() == null) {
            throw new ServiceException("工作流、发布版本和触发类型不能为空");
        }
        Long tenantId = currentTenantId();
        WorkflowDefinition definition = definitionMapper.selectById(command.definitionId());
        WorkflowVersion version = versionMapper.selectByVersionId(command.workflowVersionId());
        if (definition == null || !"0".equals(definition.getDelFlag())
                || version == null || !command.definitionId().equals(version.getDefinitionId())
                || !"PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException("工作流发布版本不存在、已退役或无权访问");
        }
        if (!Objects.equals(definition.getTenantId(), tenantId)) {
            throw new ServiceException("工作流不属于当前操作范围");
        }
        String type = command.triggerType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new ServiceException("触发类型无效");
        JsonNode config = WorkflowJsonPayload.toJsonNode(command.config(), objectMapper);
        if (config == null || !config.isObject()) throw new ServiceException("触发配置必须是JSON对象");
        validateNoSecrets(config);
        validateConfig(type, config);
        WorkflowTrigger trigger = new WorkflowTrigger();
        trigger.setTenantId(tenantId);
        trigger.setTriggerId(UUID.randomUUID().toString());
        trigger.setDefinitionId(command.definitionId());
        trigger.setWorkflowVersionId(command.workflowVersionId());
        trigger.setTriggerType(type);
        trigger.setConfigJson(writeJson(config));
        JsonNode dedupPolicy = WorkflowJsonPayload.toJsonNode(
                command.dedupPolicy(), objectMapper);
        trigger.setDedupPolicyJson(dedupPolicy == null ? "{}" : writeJson(dedupPolicy));
        trigger.setStatus("ACTIVE");
        if ("SCHEDULE".equals(type)) {
            trigger.setNextFireTime(nextFireTime(config, new Date()));
        }
        trigger.setLockVersion(0);
        trigger.setCreateBy(CallerUtils.getUsername());
        trigger.setUpdateBy(CallerUtils.getUsername());
        if (triggerMapper.insert(trigger) != 1) throw new ServiceException("创建工作流触发器失败");
        return view(trigger);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTriggerView updateStatus(
            String triggerId, WorkflowTriggerStatusCommand command) {
        requireEnabled();
        if (triggerId == null || triggerId.isBlank() || command == null
                || command.status() == null || command.expectedLockVersion() == null) {
            throw new ServiceException("触发器、目标状态和锁版本不能为空");
        }
        String status = command.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new ServiceException("触发器状态无效");
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<WorkflowTrigger> query = new LambdaQueryWrapper<>();
        applyTenantScope(query, tenantId);
        WorkflowTrigger existing = triggerMapper.selectOne(
                query.eq(WorkflowTrigger::getTriggerId, triggerId).last("LIMIT 1"));
        if (existing == null) throw new ServiceException("工作流触发器不存在或无权访问");
        Date nextFireTime = null;
        if ("ACTIVE".equals(status) && "SCHEDULE".equals(existing.getTriggerType())) {
            try {
                nextFireTime = nextFireTime(
                        objectMapper.readTree(existing.getConfigJson()), new Date());
            } catch (Exception e) {
                throw new ServiceException("定时触发器配置已损坏，无法启用");
            }
        }
        if (triggerMapper.updateStatus(triggerId, tenantId, status,
                command.expectedLockVersion(), nextFireTime, CallerUtils.getUsername()) != 1) {
            throw new ServiceException("触发器已被其他用户修改，请刷新后重试");
        }
        existing.setStatus(status);
        existing.setLockVersion(command.expectedLockVersion() + 1);
        if (nextFireTime != null) existing.setNextFireTime(nextFireTime);
        existing.setUpdateTime(new Date());
        return view(existing);
    }

    @Override
    public WorkflowExecutionView invoke(
            String triggerId, WorkflowTriggerInvocationCommand command) {
        requireEnabled();
        LambdaQueryWrapper<WorkflowTrigger> query = new LambdaQueryWrapper<>();
        applyTenantScope(query, currentTenantId());
        WorkflowTrigger trigger = triggerMapper.selectOne(
                query.eq(WorkflowTrigger::getTriggerId, triggerId)
                        .eq(WorkflowTrigger::getStatus, "ACTIVE").last("LIMIT 1"));
        if (trigger == null) throw new ServiceException("触发器不存在、已停用或无权访问");
        String callerKey = command == null ? null : command.idempotencyKey();
        String idempotencyKey = triggerIdempotencyKey(trigger, callerKey);
        JsonNode input = command == null || command.input() == null
                ? objectMapper.createObjectNode()
                : WorkflowJsonPayload.toJsonNode(command.input(), objectMapper);
        return executionFacade.start(new WorkflowExecutionStartCommand(
                trigger.getDefinitionId(), trigger.getWorkflowVersionId(), input,
                "PROD", idempotencyKey, null));
    }

    private void validateConfig(String type, JsonNode config) {
        if ("SCHEDULE".equals(type)) {
            String cron = config.path("cron").asText();
            try {
                CronExpression.parse(cron);
                ZoneId.of(config.path("timezone").asText("Asia/Shanghai"));
            } catch (Exception e) {
                throw new ServiceException("定时触发器cron或时区无效");
            }
        } else if ("WEBHOOK".equals(type)) {
            String method = config.path("method").asText("POST");
            if (!"POST".equalsIgnoreCase(method)) {
                throw new ServiceException("Webhook触发器仅允许POST");
            }
        } else if (!config.path("eventType").asText()
                .matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            throw new ServiceException("事件触发器eventType格式无效");
        }
    }

    private Date nextFireTime(JsonNode config, Date after) {
        try {
            ZoneId zone = ZoneId.of(config.path("timezone").asText("Asia/Shanghai"));
            ZonedDateTime next = CronExpression.parse(config.path("cron").asText())
                    .next(ZonedDateTime.ofInstant(after.toInstant(), zone));
            if (next == null) throw new ServiceException("定时触发器没有可计算的下次运行时间");
            return Date.from(next.toInstant());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("无法计算定时触发器下次运行时间");
        }
    }

    private void validateNoSecrets(JsonNode value) {
        value.fields().forEachRemaining(field -> {
            String normalizedKey = field.getKey().toLowerCase(Locale.ROOT)
                    .replace("-", "").replace("_", "");
            if (SECRET_KEYS.stream().anyMatch(normalizedKey::contains)) {
                throw new ServiceException("触发配置不能保存密钥，请使用资源绑定");
            }
            if (field.getValue().isObject()) validateNoSecrets(field.getValue());
            if (field.getValue().isArray()) {
                field.getValue().forEach(this::validateNoSecrets);
            }
        });
    }

    private String triggerIdempotencyKey(
            WorkflowTrigger trigger, String callerKey) {
        int windowSeconds = 0;
        try {
            windowSeconds = objectMapper.readTree(trigger.getDedupPolicyJson())
                    .path("windowSeconds").asInt(0);
        } catch (Exception ignored) {
            // 持久化策略无效时回退为调用方控制的幂等机制。
        }
        if (windowSeconds > 0 && (callerKey == null || callerKey.isBlank())) {
            throw new ServiceException("该触发器启用了去重窗口，调用时必须提供幂等键");
        }
        if (callerKey == null || callerKey.isBlank()) return null;
        boolean stableInternalKey = callerKey.startsWith("event:")
                || callerKey.startsWith("schedule:");
        long bucket = windowSeconds > 0 && !stableInternalKey
                ? System.currentTimeMillis() / (windowSeconds * 1000L) : 0;
        return "trigger:" + UUID.nameUUIDFromBytes(
                (trigger.getTriggerId() + "\u0000" + callerKey.trim() + "\u0000" + bucket)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private Long currentTenantId() {
        if (CallerUtils.isPlatformMode()) {
            try {
                Long tenantId = Long.valueOf(CallerUtils.getTenantId());
                if (tenantId <= 0) throw new NumberFormatException();
                return tenantId;
            } catch (NumberFormatException e) {
                throw new ServiceException("中台租户ID格式错误");
            }
        }
        return null;
    }

    private void applyTenantScope(LambdaQueryWrapper<WorkflowTrigger> query, Long tenantId) {
        if (tenantId == null) {
            query.isNull(WorkflowTrigger::getTenantId);
        } else {
            query.eq(WorkflowTrigger::getTenantId, tenantId);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ServiceException("工作流 尚未启用");
    }

    private String writeJson(JsonNode value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() > 65535) throw new ServiceException("触发配置不能超过64KB");
            return json;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("触发配置JSON格式无效");
        }
    }

    private WorkflowTriggerView view(WorkflowTrigger trigger) {
        return new WorkflowTriggerView(
                trigger.getTriggerId(), trigger.getTenantId(), trigger.getDefinitionId(),
                trigger.getWorkflowVersionId(), trigger.getTriggerType(), trigger.getConfigJson(),
                trigger.getDedupPolicyJson(), trigger.getStatus(), trigger.getNextFireTime(),
                trigger.getLastFireTime(), trigger.getLastExecutionId(),
                trigger.getLastTriggerStatus(), trigger.getLastErrorMessage(), trigger.getLockVersion(),
                trigger.getCreateTime(), trigger.getUpdateTime());
    }
}
