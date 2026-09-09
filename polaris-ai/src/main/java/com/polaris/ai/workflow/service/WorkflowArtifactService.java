package com.polaris.ai.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.workflow.application.WorkflowArtifactApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowArtifactContent;
import com.polaris.ai.workflow.application.WorkflowArtifactView;
import com.polaris.ai.workflow.domain.WorkflowArtifact;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.mapper.WorkflowArtifactMapper;
import com.polaris.ai.workflow.mapper.WorkflowExecutionMapper;
import com.polaris.ai.workflow.security.WorkflowDataRedactor;
import com.polaris.ai.workflow.spi.WorkflowArtifactStorage;
import com.polaris.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 基于私有存储、按租户隔离且支持幂等重试的工作流产物服务。 */
@Service
@Slf4j
public class WorkflowArtifactService implements WorkflowArtifactApplicationFacade {

    public static final int MAX_ARTIFACT_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final Set<String> TERMINAL_EXECUTION_STATUSES =
            Set.of("SUCCEEDED", "FAILED", "CANCELLED", "REJECTED");

    private final WorkflowArtifactMapper artifactMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowDataRedactor dataRedactor;
    private final WorkflowArtifactStorage artifactStorage;

    public WorkflowArtifactService(
            WorkflowArtifactMapper artifactMapper,
            WorkflowExecutionMapper executionMapper,
            ObjectMapper objectMapper,
            WorkflowDataRedactor dataRedactor,
            WorkflowArtifactStorage artifactStorage) {
        this.artifactMapper = artifactMapper;
        this.executionMapper = executionMapper;
        this.objectMapper = objectMapper;
        this.dataRedactor = dataRedactor;
        this.artifactStorage = artifactStorage;
    }

    /** 保存节点内容；同一逻辑节点运行只允许产生一个内容一致的产物。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowArtifactView storeNodeArtifact(
            Long tenantId,
            String executionId,
            String nodeRunId,
            JsonNode value,
            String format,
            String fileNameTemplate,
            int retentionDays) {
        PreparedArtifact prepared = prepare(value, format, fileNameTemplate,
                executionId, nodeRunId);
        return storePrepared(tenantId, executionId, nodeRunId, "result",
                "WORKFLOW_NODE", prepared, retentionDays);
    }

    /** 使用正式规则完成脱敏、序列化和限制校验，但不写数据库或文件。 */
    public PreparedArtifact previewNodeArtifact(
            JsonNode value,
            String format,
            String fileNameTemplate) {
        return prepare(value, format, fileNameTemplate, "preview", "preview");
    }

    /** 审批大快照复用同一私有产物能力，但占用独立逻辑槽位。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowArtifactView storeJson(
            Long tenantId,
            String executionId,
            String nodeRunId,
            JsonNode value,
            String requestedFileName,
            String requestedMimeType,
            int retentionDays) {
        if (requestedMimeType != null && !requestedMimeType.isBlank()
                && !"application/json".equalsIgnoreCase(requestedMimeType.trim())) {
            throw new ServiceException("JSON产物只能使用application/json媒体类型");
        }
        PreparedArtifact prepared = prepare(value, "JSON", requestedFileName,
                executionId, nodeRunId);
        return storePrepared(tenantId, executionId, nodeRunId, "approval_snapshot",
                "APPROVAL_SNAPSHOT", prepared, retentionDays);
    }

    private WorkflowArtifactView storePrepared(
            Long tenantId,
            String executionId,
            String nodeRunId,
            String slotKey,
            String sourceType,
            PreparedArtifact prepared,
            int requestedRetentionDays) {
        if (executionId == null || executionId.isBlank()
                || nodeRunId == null || nodeRunId.isBlank()) {
            throw new ServiceException("工作流执行ID和节点运行ID不能为空");
        }
        WorkflowArtifact existing = artifactMapper.selectOne(
                new LambdaQueryWrapper<WorkflowArtifact>()
                        .eq(WorkflowArtifact::getExecutionId, executionId)
                        .eq(WorkflowArtifact::getNodeRunId, nodeRunId)
                        .eq(WorkflowArtifact::getSlotKey, slotKey)
                        .last("LIMIT 1"));
        if (existing != null) {
            if (!prepared.sha256().equals(existing.getContentHash())) {
                throw new ServiceException("同一节点运行已保存不同内容，请重新发起工作流执行");
            }
            if (!"AVAILABLE".equals(existing.getStatus())) {
                throw new ServiceException("工作流产物正在处理或已不可用，请稍后重试");
            }
            return view(existing);
        }

        int retentionDays = Math.max(1, Math.min(requestedRetentionDays, 365));
        String artifactId = UUID.nameUUIDFromBytes(
                (executionId + "\u0000" + nodeRunId + "\u0000" + slotKey)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        String tenantSegment = tenantId == null ? "admin" : "tenant_" + tenantId;
        Path relative = Path.of(tenantSegment, safeToken(executionId),
                artifactId + "." + prepared.extension());
        try {
            WorkflowArtifact artifact = new WorkflowArtifact();
            artifact.setTenantId(tenantId);
            artifact.setArtifactId(artifactId);
            artifact.setExecutionId(executionId);
            artifact.setNodeRunId(nodeRunId);
            artifact.setSlotKey(slotKey);
            artifact.setSourceType(sourceType);
            artifact.setFileName(prepared.fileName());
            artifact.setStorageRef(relative.toString());
            artifact.setMimeType(prepared.mediaType());
            artifact.setSizeBytes((long) prepared.content().length);
            artifact.setContentHash(prepared.sha256());
            artifact.setStatus("PREPARING");
            artifact.setRetentionDays(retentionDays);
            artifact.setExpiresTime(null);
            artifact.setCreateTime(new Date());
            if (artifactMapper.insert(artifact) != 1) {
                throw new ServiceException("保存工作流产物元数据失败");
            }
            artifactStorage.store(relative.toString(), prepared.content());
            artifact.setStatus("AVAILABLE");
            artifactMapper.updateById(artifact);
            return view(artifact);
        } catch (Exception e) {
            if (e instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("保存工作流产物失败: " + e.getMessage());
        }
    }

    public PreparedArtifact prepare(
            JsonNode value,
            String requestedFormat,
            String requestedFileName,
            String executionId,
            String nodeRunId) {
        String format = normalizeFormat(requestedFormat);
        byte[] content;
        try {
            JsonNode redacted = dataRedactor.redact(value);
            if ("TEXT".equals(format)) {
                content = (redacted != null && redacted.isTextual()
                        ? redacted.asText() : objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(redacted)).getBytes(StandardCharsets.UTF_8);
            } else {
                content = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(redacted);
            }
        } catch (Exception e) {
            throw new ServiceException("工作流产物序列化失败");
        }
        if (content.length > MAX_ARTIFACT_BYTES) {
            throw new ServiceException("单个工作流产物不能超过20MB");
        }
        String extension = "TEXT".equals(format) ? "txt" : "json";
        String mediaType = "TEXT".equals(format)
                ? "text/plain;charset=UTF-8" : "application/json";
        String fileName = normalizeFileName(
                resolveFileNameTemplate(requestedFileName, executionId, nodeRunId), extension);
        return new PreparedArtifact(fileName, mediaType, extension,
                content, sha256(content));
    }

    @Override
    public List<WorkflowArtifactView> list(String executionId) {
        requireExecution(executionId);
        return artifactMapper.selectList(new LambdaQueryWrapper<WorkflowArtifact>()
                        .eq(WorkflowArtifact::getExecutionId, executionId)
                        .orderByAsc(WorkflowArtifact::getCreateTime))
                .stream().map(this::view).toList();
    }

    @Override
    public WorkflowArtifactContent load(String executionId, String artifactId) {
        requireExecution(executionId);
        WorkflowArtifact artifact = artifactMapper.selectOne(
                new LambdaQueryWrapper<WorkflowArtifact>()
                        .eq(WorkflowArtifact::getExecutionId, executionId)
                        .eq(WorkflowArtifact::getArtifactId, artifactId)
                        .last("LIMIT 1"));
        if (artifact == null || !"AVAILABLE".equals(artifact.getStatus())) {
            throw new ServiceException("工作流产物不存在或已不可用");
        }
        if (artifact.getExpiresTime() != null && artifact.getExpiresTime().before(new Date())) {
            throw new ServiceException("工作流产物已过期");
        }
        try {
            byte[] content = artifactStorage.load(artifact.getStorageRef());
            if (content.length > MAX_ARTIFACT_BYTES
                    || !sha256(content).equals(artifact.getContentHash())) {
                throw new ServiceException("工作流产物完整性校验失败");
            }
            return new WorkflowArtifactContent(
                    artifact.getFileName(), artifact.getMimeType(), content);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("读取工作流产物失败");
        }
    }

    /** 工作流结束后才开始计算保留期，到期后清理物理内容并保留可审计状态。 */
    @Scheduled(fixedDelayString = "${ai.workflow.artifact-cleanup-poll-ms:60000}")
    public void maintainArtifacts() {
        Date now = new Date();
        List<WorkflowArtifact> pendingExpiry = artifactMapper.selectList(
                new LambdaQueryWrapper<WorkflowArtifact>()
                        .eq(WorkflowArtifact::getStatus, "AVAILABLE")
                        .isNull(WorkflowArtifact::getExpiresTime)
                        .last("LIMIT 200"));
        for (WorkflowArtifact artifact : pendingExpiry) {
            WorkflowExecution execution = executionMapper.selectByExecutionId(
                    artifact.getExecutionId());
            if (execution == null || !TERMINAL_EXECUTION_STATUSES.contains(execution.getStatus())) {
                continue;
            }
            Date finishedAt = execution.getFinishTime() == null ? now : execution.getFinishTime();
            int retentionDays = artifact.getRetentionDays() == null
                    ? DEFAULT_RETENTION_DAYS : artifact.getRetentionDays();
            artifact.setExpiresTime(new Date(finishedAt.getTime()
                    + TimeUnit.DAYS.toMillis(retentionDays)));
            artifactMapper.updateById(artifact);
        }

        List<WorkflowArtifact> expired = artifactMapper.selectList(
                new LambdaQueryWrapper<WorkflowArtifact>()
                        .eq(WorkflowArtifact::getStatus, "AVAILABLE")
                        .isNotNull(WorkflowArtifact::getExpiresTime)
                        .le(WorkflowArtifact::getExpiresTime, now)
                        .last("LIMIT 200"));
        for (WorkflowArtifact artifact : expired) {
            try {
                artifact.setStatus("DELETING");
                artifactMapper.updateById(artifact);
                artifactStorage.delete(artifact.getStorageRef());
                artifact.setStatus("DELETED");
                artifactMapper.updateById(artifact);
            } catch (Exception exception) {
                log.warn("Unable to clean workflow artifact {}: {}",
                        artifact.getArtifactId(), exception.getMessage());
                artifact.setStatus("AVAILABLE");
                artifactMapper.updateById(artifact);
            }
        }
    }

    private WorkflowExecution requireExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new ServiceException("工作流执行ID不能为空");
        }
        WorkflowExecution execution = executionMapper.selectByExecutionId(executionId);
        if (execution == null) {
            throw new ServiceException("工作流执行不存在或无权访问");
        }
        return execution;
    }

    private String normalizeFormat(String value) {
        String result = value == null ? "JSON" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("JSON", "TEXT").contains(result)) {
            throw new ServiceException("保存格式仅支持JSON或纯文本");
        }
        return result;
    }

    private String resolveFileNameTemplate(
            String value, String executionId, String nodeRunId) {
        String template = value == null || value.isBlank()
                ? "工作流产物-{{date}}" : value.trim();
        String resolved = template
                .replace("{{date}}", LocalDate.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.BASIC_ISO_DATE))
                .replace("{{executionId}}", safeToken(executionId))
                .replace("{{nodeRunId}}", safeToken(nodeRunId));
        if (resolved.matches(".*\\{\\{[^{}]+}}.*")) {
            throw new ServiceException("文件名包含不支持的变量");
        }
        return resolved;
    }

    private String safeToken(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String normalizeFileName(String value, String extension) {
        String result = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (result.isBlank()) result = "工作流产物";
        String suffix = "." + extension;
        if (!result.toLowerCase(Locale.ROOT).endsWith(suffix)) result += suffix;
        int maxBaseLength = 200 - suffix.length();
        if (result.length() > 200) {
            result = result.substring(0, Math.max(1, maxBaseLength)) + suffix;
        }
        return result;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new ServiceException("工作流产物摘要计算失败");
        }
    }

    private WorkflowArtifactView view(WorkflowArtifact artifact) {
        return new WorkflowArtifactView(
                artifact.getArtifactId(), artifact.getExecutionId(), artifact.getNodeRunId(),
                artifact.getSourceType(), artifact.getFileName(), artifact.getMimeType(),
                artifact.getSizeBytes(), artifact.getContentHash(), artifact.getStatus(),
                artifact.getExpiresTime(), artifact.getCreateTime());
    }

    public record PreparedArtifact(
            String fileName,
            String mediaType,
            String extension,
            byte[] content,
            String sha256) {
    }
}
