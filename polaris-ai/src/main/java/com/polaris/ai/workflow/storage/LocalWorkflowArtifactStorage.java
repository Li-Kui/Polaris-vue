package com.polaris.ai.workflow.storage;

import com.polaris.ai.workflow.spi.WorkflowArtifactStorage;
import com.polaris.common.exception.ServiceException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 基于本地配置目录的工作流产物存储实现。 */
public class LocalWorkflowArtifactStorage implements WorkflowArtifactStorage {

    private final Path storageRoot;

    public LocalWorkflowArtifactStorage(String profile) {
        this.storageRoot = Path.of(profile).toAbsolutePath().normalize()
                .resolve("upload").resolve("workflow_artifact").normalize();
    }

    @Override
    public void store(String storageRef, byte[] content) {
        Path target = resolveStorage(storageRef);
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, content);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
                // 保留最初的存储失败原因。
            }
            throw new ServiceException("保存工作流产物内容失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] load(String storageRef) {
        try {
            return Files.readAllBytes(resolveStorage(storageRef));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("读取工作流产物内容失败");
        }
    }

    @Override
    public void delete(String storageRef) {
        try {
            Files.deleteIfExists(resolveStorage(storageRef));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("删除工作流产物内容失败");
        }
    }

    private Path resolveStorage(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            throw new ServiceException("工作流产物存储引用不能为空");
        }
        Path result = storageRoot.resolve(storageRef).normalize();
        if (!result.startsWith(storageRoot)) {
            throw new ServiceException("工作流产物存储引用无效");
        }
        return result;
    }
}
