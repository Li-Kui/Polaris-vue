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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 基于本地配置目录和数据库元数据、按租户隔离的私有产物存储服务。 */
@Service
public class WorkflowArtifactService implements WorkflowArtifactApplicationFacade {

    private static final int MAX_ARTIFACT_BYTES = 20 * 1024 * 1024;

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

    @Transactional(rollbackFor = Exception.class)
    public WorkflowArtifactView storeJson(
            Long tenantId,
            String executionId,
            String nodeRunId,
            JsonNode value,
            String requestedFileName,
            String requestedMimeType,
            int retentionDays) {
        WorkflowArtifact existing = artifactMapper.selectOne(
                new LambdaQueryWrapper<WorkflowArtifact>()
                        .eq(WorkflowArtifact::getExecutionId, executionId)
                        .eq(WorkflowArtifact::getNodeRunId, nodeRunId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return view(existing);
        }
        byte[] content;
        try {
            content = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(dataRedactor.redact(value));
        } catch (Exception e) {
            throw new ServiceException("工作流产物序列化失败");
        }
        if (content.length > MAX_ARTIFACT_BYTES) {
            throw new ServiceException("单个工作流产物不能超过20MB");
        }
        String artifactId = UUID.nameUUIDFromBytes(
                (executionId + "\u0000" + nodeRunId).getBytes(StandardCharsets.UTF_8)).toString();
        String fileName = normalizeFileName(requestedFileName, artifactId);
        String mimeType = normalizeMimeType(requestedMimeType);
        String tenantSegment = tenantId == null ? "admin" : "tenant_" + tenantId;
        Path relative = Path.of(tenantSegment, executionId, artifactId + ".json");
        try {
            WorkflowArtifact artifact = new WorkflowArtifact();
            artifact.setTenantId(tenantId);
            artifact.setArtifactId(artifactId);
            artifact.setExecutionId(executionId);
            artifact.setNodeRunId(nodeRunId);
            artifact.setFileName(fileName);
            artifact.setStorageRef(relative.toString());
            artifact.setMimeType(mimeType);
            artifact.setSizeBytes((long) content.length);
            artifact.setContentHash(sha256(content));
            artifact.setExpiresTime(new Date(System.currentTimeMillis()
                    + TimeUnit.DAYS.toMillis(Math.max(1, Math.min(retentionDays, 3650)))));
            artifact.setCreateTime(new Date());
            if (artifactMapper.insert(artifact) != 1) {
                throw new ServiceException("保存工作流产物元数据失败");
            }
            artifactStorage.store(relative.toString(), content);
            return view(artifact);
        } catch (Exception e) {
            if (e instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("保存工作流产物失败: " + e.getMessage());
        }
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
        if (artifact == null) {
            throw new ServiceException("工作流产物不存在或无权访问");
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

    private String normalizeFileName(String value, String artifactId) {
        String result = value == null || value.isBlank()
                ? "workflow-artifact-" + artifactId + ".json" : value.trim();
        result = result.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (result.length() > 200) {
            result = result.substring(0, 200) + ".json";
        }
        return result;
    }

    private String normalizeMimeType(String value) {
        String result = value == null || value.isBlank()
                ? "application/json" : value.trim().toLowerCase();
        if (!result.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")) {
            throw new ServiceException("工作流产物MIME类型无效");
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
                artifact.getFileName(), artifact.getMimeType(), artifact.getSizeBytes(),
                artifact.getContentHash(), artifact.getExpiresTime(), artifact.getCreateTime());
    }
}
