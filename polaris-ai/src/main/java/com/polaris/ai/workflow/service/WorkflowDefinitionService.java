package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.compiler.WorkflowDefinitionCompiler;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.contract.WorkflowSchemaVersions;
import com.polaris.ai.workflow.definition.WorkflowCompilationResult;
import com.polaris.ai.workflow.definition.WorkflowDiagnostic;
import com.polaris.ai.workflow.domain.WorkflowDefinition;
import com.polaris.ai.workflow.domain.WorkflowVersion;
import com.polaris.ai.workflow.mapper.WorkflowDefinitionMapper;
import com.polaris.ai.workflow.mapper.WorkflowVersionMapper;
import com.polaris.ai.workflow.runtime.WorkflowApprovalDirectoryResolver;
import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptor;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptorResolver;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 工作流草稿、校验和不可变版本发布服务。 */
@Service
public class WorkflowDefinitionService implements WorkflowDefinitionApplicationFacade {

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowDefinitionCompiler compiler;
    private final WorkflowNodeDescriptorResolver descriptors;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<WorkflowNodeSchemaApplicationFacade> nodeSchemaProvider;
    private final ObjectProvider<WorkflowApprovalDirectoryResolver> approvalDirectoryProvider;

    @Autowired
    public WorkflowDefinitionService(
            WorkflowDefinitionMapper definitionMapper,
            WorkflowVersionMapper versionMapper,
            WorkflowDefinitionCompiler compiler,
            WorkflowNodeDescriptorResolver descriptors,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<WorkflowNodeSchemaApplicationFacade> nodeSchemaProvider,
            ObjectProvider<WorkflowApprovalDirectoryResolver> approvalDirectoryProvider) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.compiler = compiler;
        this.descriptors = descriptors;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.nodeSchemaProvider = nodeSchemaProvider;
        this.approvalDirectoryProvider = approvalDirectoryProvider;
    }

    WorkflowDefinitionService(
            WorkflowDefinitionMapper definitionMapper,
            WorkflowVersionMapper versionMapper,
            WorkflowDefinitionCompiler compiler,
            WorkflowNodeDescriptorResolver descriptors,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<WorkflowNodeSchemaApplicationFacade> nodeSchemaProvider) {
        this(definitionMapper, versionMapper, compiler, descriptors,
                properties, objectMapper, nodeSchemaProvider, null);
    }

    WorkflowDefinitionService(
            WorkflowDefinitionMapper definitionMapper,
            WorkflowVersionMapper versionMapper,
            WorkflowDefinitionCompiler compiler,
            WorkflowNodeDescriptorResolver descriptors,
            WorkflowProperties properties,
            ObjectMapper objectMapper) {
        this(definitionMapper, versionMapper, compiler, descriptors,
                properties, objectMapper, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionView createDraft(WorkflowDraftCommand command) {
        requireEnabled();
        DraftMetadata metadata = parseDraftMetadata(command == null ? null : command.definitionJson());
        Long tenantId = currentTenantId();
        String ownerType = tenantId == null ? "SYSTEM" : "TENANT";
        long ownerId = tenantId == null ? 0L : tenantId;
        Long duplicates = definitionMapper.selectCount(new LambdaQueryWrapper<WorkflowDefinition>()
                .eq(WorkflowDefinition::getOwnerType, ownerType)
                .eq(WorkflowDefinition::getOwnerId, ownerId)
                .eq(WorkflowDefinition::getWorkflowCode, metadata.code())
                .eq(WorkflowDefinition::getDelFlag, "0"));
        if (duplicates != null && duplicates > 0) {
            throw new ServiceException("当前所有者范围内工作流编码已存在");
        }
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setTenantId(tenantId);
        definition.setOwnerType(ownerType);
        definition.setOwnerId(ownerId);
        definition.setWorkflowCode(metadata.code());
        definition.setWorkflowName(metadata.name());
        definition.setDescription(metadata.description());
        definition.setTagsJson(metadata.tagsJson());
        definition.setDraftSchemaVersion(metadata.schemaVersion());
        definition.setDraftJson(command.definitionJson());
        definition.setDraftRevision(1L);
        definition.setStatus("DRAFT");
        definition.setLockVersion(0);
        definition.setDelFlag("0");
        definition.setCreateBy(CallerUtils.getUsername());
        definition.setUpdateBy(CallerUtils.getUsername());
        if (definitionMapper.insert(definition) != 1) {
            throw new ServiceException("创建工作流草稿失败");
        }
        return view(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionView updateDraft(Long definitionId, WorkflowDraftCommand command) {
        requireEnabled();
        if (definitionId == null || command == null || command.expectedRevision() == null) {
            throw new ServiceException("工作流ID和预期草稿修订号不能为空");
        }
        WorkflowDefinition existing = requireDefinition(definitionId);
        DraftMetadata metadata = parseDraftMetadata(command.definitionJson());
        if (!existing.getWorkflowCode().equals(metadata.code())) {
            throw new ServiceException("工作流编码发布前后均不可通过草稿更新修改");
        }
        int updated = definitionMapper.updateDraft(
                definitionId,
                command.expectedRevision(),
                metadata.name(),
                metadata.description(),
                metadata.tagsJson(),
                metadata.schemaVersion(),
                command.definitionJson(),
                CallerUtils.getUsername());
        if (updated != 1) {
            throw new ServiceException("草稿已被其他用户修改，请刷新后合并变更");
        }
        return view(requireDefinition(definitionId));
    }

    @Override
    public WorkflowDefinitionView getDefinition(Long definitionId) {
        requireEnabled();
        return view(requireDefinition(definitionId));
    }

    @Override
    public List<WorkflowDefinitionView> listDefinitions() {
        requireEnabled();
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<WorkflowDefinition> query =
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getDelFlag, "0");
        if (tenantId == null) {
            query.isNull(WorkflowDefinition::getTenantId);
        } else {
            query.eq(WorkflowDefinition::getTenantId, tenantId);
        }
        return definitionMapper.selectList(query
                        .orderByDesc(WorkflowDefinition::getUpdateTime)
                        .orderByDesc(WorkflowDefinition::getId))
                .stream().map(this::view).toList();
    }

    @Override
    public WorkflowCompilationResult validateDraft(Long definitionId) {
        requireEnabled();
        WorkflowDefinition definition = requireDefinition(definitionId);
        return compiler.compile(definition.getDraftJson(),
                "draft:" + definition.getId() + ":" + definition.getDraftRevision());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowPublishResult publish(Long definitionId, WorkflowPublishCommand command) {
        requireEnabled();
        if (definitionId == null || command == null || command.expectedRevision() == null) {
            throw new ServiceException("工作流ID和预期草稿修订号不能为空");
        }
        WorkflowDefinition definition = definitionMapper.selectByIdForUpdate(definitionId);
        if (definition == null || !"0".equals(definition.getDelFlag())
                || !Objects.equals(definition.getTenantId(), currentTenantId())) {
            throw new ServiceException("工作流不存在或无权访问");
        }
        if (!command.expectedRevision().equals(definition.getDraftRevision())) {
            throw new ServiceException("草稿已发生变化，请重新校验后发布");
        }
        String versionId = UUID.randomUUID().toString();
        WorkflowCompilationResult compilation = compiler.compile(definition.getDraftJson(), versionId);
        if (!compilation.isValid()) {
            return new WorkflowPublishResult(false, null, compilation.diagnostics());
        }
        List<WorkflowResolvedNodeSchemaView> schemaSnapshots;
        try {
            schemaSnapshots = resolvePublishSchemas(definitionId);
        } catch (Exception e) {
            return new WorkflowPublishResult(false, null, List.of(WorkflowDiagnostic.error(
                    "DYNAMIC_SCHEMA_RESOLUTION_FAILED", null, "$.nodes",
                    "发布时无法解析节点 Schema，请检查节点配置和资源绑定")));
        }
        compilation = compiler.compile(definition.getDraftJson(), versionId, schemaSnapshots);
        if (!compilation.isValid()) {
            return new WorkflowPublishResult(false, null, compilation.diagnostics());
        }
        List<WorkflowDiagnostic> approvalDirectoryDiagnostics = validateApprovalTargets(
                definition.getDraftJson(), definition.getTenantId());
        if (!approvalDirectoryDiagnostics.isEmpty()) {
            return new WorkflowPublishResult(false, null, approvalDirectoryDiagnostics);
        }
        List<WorkflowDiagnostic> publishDiagnostics = publishDiagnostics(
                compilation.diagnostics(), schemaSnapshots);
        WorkflowVersion existingVersion = versionMapper.selectByDefinitionAndContentHash(
                definition.getId(), compilation.plan().getContentHash());
        if (existingVersion != null) {
            if (!Objects.equals(definition.getCurrentPublishedVersionId(), existingVersion.getVersionId())
                    || !"ACTIVE".equals(definition.getStatus())) {
                definition.setCurrentPublishedVersionId(existingVersion.getVersionId());
                definition.setStatus("ACTIVE");
                definition.setUpdateBy(CallerUtils.getUsername());
                if (definitionMapper.updateById(definition) != 1) {
                    throw new ServiceException("工作流发布状态更新失败");
                }
            }
            List<WorkflowDiagnostic> diagnostics = new ArrayList<>(publishDiagnostics);
            diagnostics.add(WorkflowDiagnostic.warning(
                    "PUBLISH_CONTENT_UNCHANGED", null, "$.metadata",
                    "当前草稿内容已发布，已复用版本 v" + existingVersion.getVersionNo()
                            + "，未重复创建新版本"));
            return new WorkflowPublishResult(true, versionView(existingVersion), diagnostics);
        }
        WorkflowVersion version = new WorkflowVersion();
        version.setVersionId(versionId);
        version.setTenantId(definition.getTenantId());
        version.setDefinitionId(definition.getId());
        version.setVersionNo(versionMapper.selectNextVersionNo(definition.getId()));
        version.setSchemaVersion(WorkflowSchemaVersions.DEFINITION);
        version.setDefinitionJson(definition.getDraftJson());
        version.setPlanSchemaVersion(WorkflowSchemaVersions.EXECUTION_PLAN);
        version.setExecutionPlanJson(writeJson(compilation.plan()));
        version.setContentHash(compilation.plan().getContentHash());
        version.setStatus("PUBLISHED");
        version.setPublishedBy(CallerUtils.getUsername());
        version.setPublishedTime(new Date());
        version.setCreateBy(CallerUtils.getUsername());
        version.setUpdateBy(CallerUtils.getUsername());
        if (versionMapper.insert(version) != 1) {
            throw new ServiceException("创建工作流发布版本失败");
        }
        definition.setCurrentPublishedVersionId(versionId);
        definition.setStatus("ACTIVE");
        definition.setUpdateBy(CallerUtils.getUsername());
        if (definitionMapper.updateById(definition) != 1) {
            throw new ServiceException("工作流发布状态更新失败");
        }
        return new WorkflowPublishResult(true, versionView(version), publishDiagnostics);
    }

    @Override
    public List<WorkflowPublishedVersionView> listVersions(Long definitionId) {
        requireEnabled();
        requireDefinition(definitionId);
        return versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getDefinitionId, definitionId)
                        .orderByDesc(WorkflowVersion::getVersionNo))
                .stream().map(this::versionView).toList();
    }

    @Override
    public WorkflowPublishedVersionDetailView getVersion(
            Long definitionId, String versionId) {
        requireEnabled();
        requireDefinition(definitionId);
        WorkflowVersion version = versionMapper.selectByVersionId(versionId);
        if (version == null || !definitionId.equals(version.getDefinitionId())) {
            throw new ServiceException("工作流发布版本不存在或无权访问");
        }
        return new WorkflowPublishedVersionDetailView(
                version.getVersionId(), version.getDefinitionId(), version.getVersionNo(),
                version.getSchemaVersion(), version.getDefinitionJson(), version.getContentHash(),
                version.getStatus(), version.getPublishedBy(), version.getPublishedTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionView rollbackDraft(
            Long definitionId, String versionId, WorkflowRollbackCommand command) {
        requireEnabled();
        if (command == null || command.expectedRevision() == null
                || versionId == null || versionId.isBlank()) {
            throw new ServiceException("发布版本和预期草稿修订号不能为空");
        }
        WorkflowDefinition definition = requireDefinition(definitionId);
        WorkflowVersion version = versionMapper.selectByVersionId(versionId);
        if (version == null || !definitionId.equals(version.getDefinitionId())
                || !"PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException("工作流发布版本不存在、已退役或不属于当前定义");
        }
        return updateDraft(definitionId,
                new WorkflowDraftCommand(version.getDefinitionJson(), command.expectedRevision()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionView cloneDefinition(
            Long definitionId, WorkflowCloneCommand command) {
        requireEnabled();
        if (command == null || command.workflowCode() == null
                || command.workflowName() == null) {
            throw new ServiceException("新工作流编码和名称不能为空");
        }
        WorkflowDefinition source = requireDefinition(definitionId);
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root =
                    (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                            source.getDraftJson());
            com.fasterxml.jackson.databind.node.ObjectNode metadata = root.with("metadata");
            metadata.put("code", command.workflowCode().trim());
            metadata.put("name", command.workflowName().trim());
            return createDraft(new WorkflowDraftCommand(
                    objectMapper.writeValueAsString(root), null));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("克隆工作流定义失败: " + e.getMessage());
        }
    }

    @Override
    public Collection<WorkflowNodeDescriptor> listNodeDescriptors() {
        requireEnabled();
        return descriptors.list();
    }

    private List<WorkflowResolvedNodeSchemaView> resolvePublishSchemas(Long definitionId) {
        if (nodeSchemaProvider == null) return List.of();
        WorkflowNodeSchemaApplicationFacade facade = nodeSchemaProvider.getIfAvailable();
        return facade == null ? List.of() : facade.resolve(definitionId, "PROD");
    }

    private List<WorkflowDiagnostic> publishDiagnostics(
            List<WorkflowDiagnostic> compilationDiagnostics,
            List<WorkflowResolvedNodeSchemaView> snapshots) {
        List<WorkflowDiagnostic> result = new ArrayList<>(
                compilationDiagnostics == null ? List.of() : compilationDiagnostics);
        for (WorkflowResolvedNodeSchemaView snapshot
                : snapshots == null ? List.<WorkflowResolvedNodeSchemaView>of() : snapshots) {
            for (String message : snapshot.diagnostics()) {
                result.add(WorkflowDiagnostic.warning(
                        "DYNAMIC_SCHEMA_RESOLUTION_WARNING", snapshot.nodeId(),
                        "$.nodes." + snapshot.nodeId(), message));
            }
        }
        return List.copyOf(result);
    }

    private List<WorkflowDiagnostic> validateApprovalTargets(String definitionJson, Long tenantId) {
        if (approvalDirectoryProvider == null) return List.of();
        WorkflowApprovalDirectoryResolver resolver = approvalDirectoryProvider.getIfAvailable();
        if (resolver == null) return List.of();
        List<WorkflowDiagnostic> diagnostics = new ArrayList<>();
        try {
            JsonNode nodes = objectMapper.readTree(definitionJson).path("nodes");
            for (JsonNode node : nodes) {
                if (!"approval".equals(node.path("type").asText())) continue;
                String nodeId = node.path("id").asText();
                JsonNode config = node.path("config");
                int stageIndex = 0;
                for (JsonNode stage : config.path("stages")) {
                    validateTargetSet(resolver, tenantId, nodeId,
                            "$.nodes." + nodeId + ".config.stages[" + stageIndex + "].targets",
                            stage.path("targets"), true, diagnostics);
                    validateTargetSet(resolver, tenantId, nodeId,
                            "$.nodes." + nodeId + ".config.stages[" + stageIndex + "].fallbackTargets",
                            stage.path("fallbackTargets"), false, diagnostics);
                    stageIndex++;
                }
                if ("REASSIGN".equals(config.path("expirationPolicy").path("action").asText())) {
                    validateTargetSet(resolver, tenantId, nodeId,
                            "$.nodes." + nodeId + ".config.expirationPolicy.targets",
                            config.path("expirationPolicy").path("targets"), true, diagnostics);
                }
            }
        } catch (Exception exception) {
            diagnostics.add(WorkflowDiagnostic.error("APPROVAL_DIRECTORY_VALIDATION_FAILED",
                    null, "$.nodes", "发布时无法校验审批人员目录，请刷新后重试"));
        }
        return diagnostics;
    }

    private void validateTargetSet(
            WorkflowApprovalDirectoryResolver resolver,
            Long tenantId,
            String nodeId,
            String path,
            JsonNode targets,
            boolean required,
            List<WorkflowDiagnostic> diagnostics) {
        if (!targets.isArray() || targets.isEmpty()) {
            if (required) diagnostics.add(WorkflowDiagnostic.error(
                    "APPROVAL_TARGET_UNAVAILABLE", nodeId, path, "请选择可用的审批对象"));
            return;
        }
        for (JsonNode target : targets) {
            List<String> ids = new ArrayList<>();
            target.path("ids").forEach(id -> ids.add(id.asText()));
            WorkflowApprovalTarget value = new WorkflowApprovalTarget(
                    target.path("type").asText(), ids,
                    target.path("includeChildren").asBoolean(false));
            try {
                if (resolver.resolve(tenantId, List.of(value)).isEmpty()) {
                    diagnostics.add(WorkflowDiagnostic.error("APPROVAL_TARGET_UNAVAILABLE",
                            nodeId, path, "审批对象已停用、已删除或当前没有有效成员"));
                }
            } catch (RuntimeException exception) {
                diagnostics.add(WorkflowDiagnostic.error("APPROVAL_TARGET_UNAVAILABLE",
                        nodeId, path, "审批对象不可用：" + exception.getMessage()));
            }
        }
    }

    private WorkflowDefinition requireDefinition(Long definitionId) {
        if (definitionId == null) {
            throw new ServiceException("工作流ID不能为空");
        }
        WorkflowDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null || !"0".equals(definition.getDelFlag())
                || !Objects.equals(definition.getTenantId(), currentTenantId())) {
            throw new ServiceException("工作流不存在或无权访问");
        }
        return definition;
    }

    private DraftMetadata parseDraftMetadata(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new ServiceException("工作流定义不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode metadata = root.path("metadata");
            String schemaVersion = root.path("schemaVersion").asText();
            String code = metadata.path("code").asText();
            String name = metadata.path("name").asText();
            if (!WorkflowSchemaVersions.DEFINITION.equals(schemaVersion)) {
                throw new ServiceException("仅支持 WorkflowDefinition schemaVersion 2.0");
            }
            if (!code.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                throw new ServiceException("工作流编码格式无效");
            }
            if (name.isBlank() || name.length() > 128) {
                throw new ServiceException("工作流名称不能为空且不能超过128个字符");
            }
            String description = metadata.path("description").isMissingNode()
                    ? null : metadata.path("description").asText();
            String tagsJson = metadata.path("tags").isArray()
                    ? objectMapper.writeValueAsString(metadata.path("tags")) : "[]";
            return new DraftMetadata(schemaVersion, code, name, description, tagsJson);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("工作流定义JSON格式无效: " + e.getMessage());
        }
    }

    private WorkflowDefinitionView view(WorkflowDefinition definition) {
        return new WorkflowDefinitionView(
                definition.getId(),
                definition.getTenantId(),
                definition.getOwnerType(),
                definition.getWorkflowCode(),
                definition.getWorkflowName(),
                definition.getDescription(),
                definition.getDraftSchemaVersion(),
                definition.getDraftJson(),
                definition.getDraftRevision(),
                definition.getCurrentPublishedVersionId(),
                definition.getStatus(),
                definition.getLockVersion(),
                definition.getCreateTime(),
                definition.getUpdateTime());
    }

    private WorkflowPublishedVersionView versionView(WorkflowVersion version) {
        return new WorkflowPublishedVersionView(
                version.getVersionId(),
                version.getDefinitionId(),
                version.getVersionNo(),
                version.getSchemaVersion(),
                version.getPlanSchemaVersion(),
                version.getContentHash(),
                version.getStatus(),
                version.getPublishedBy(),
                version.getPublishedTime());
    }

    private Long currentTenantId() {
        if (!CallerUtils.isPlatformMode()) {
            return null;
        }
        String tenantId = CallerUtils.getTenantId();
        try {
            long value = Long.parseLong(tenantId);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (Exception e) {
            throw new ServiceException("中台租户ID格式错误");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ServiceException("工作流 尚未启用");
        }
        if (CallerUtils.isPlatformMode()
                && !properties.isEnabledForTenant(currentTenantId())) {
            throw new ServiceException("当前租户尚未启用 工作流");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("工作流执行计划序列化失败: " + e.getMessage());
        }
    }

    private record DraftMetadata(
            String schemaVersion,
            String code,
            String name,
            String description,
            String tagsJson) {
    }
}
