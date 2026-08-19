package com.polaris.ai.safety.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class DefaultSafeFileOperations implements SafeFileOperations {

    private static final Logger log = LoggerFactory.getLogger(DefaultSafeFileOperations.class);

    private final PrivateStoragePaths storagePaths;

    public DefaultSafeFileOperations(PrivateStoragePaths storagePaths) {
        this.storagePaths = storagePaths;
    }

    @Override
    public void move(Path source, Path destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("源路径与目标路径均不能为空");
        }
        storagePaths.assertWithinPrivateRoot(source);
        storagePaths.assertWithinPrivateRoot(destination);

        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("源文件不存在: " + source);
        }
        if (Files.isSymbolicLink(source) || Files.isSymbolicLink(destination)) {
            throw new SecurityException("禁止对符号链接执行文件移动操作");
        }

        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("移动私有文件失败, source={}, destination={}", source, destination, e);
            throw new IllegalStateException("移动私有文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Path target) {
        if (target == null) {
            return;
        }
        storagePaths.assertWithinPrivateRoot(target);
        if (Files.isSymbolicLink(target)) {
            throw new SecurityException("禁止对符号链接执行删除操作");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.error("删除私有文件失败, target={}", target, e);
            throw new IllegalStateException("删除私有文件失败: " + e.getMessage(), e);
        }
    }
}
