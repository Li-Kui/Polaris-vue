package com.polaris.ai.safety.service;

import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.common.config.PolarisConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PrivateStoragePaths {

    private final ModerationProperties properties;

    public PrivateStoragePaths(ModerationProperties properties) {
        this.properties = properties;
    }

    public Path privateRoot() {
        String configured = properties.getStorageRoot();
        Path root = (configured == null || configured.isBlank())
                ? Path.of(PolarisConfig.getProfile() + "-private")
                : Path.of(configured);
        return root.toAbsolutePath().normalize();
    }

    public Path stagedAttachmentsDir() {
        return privateRoot().resolve("attachments/staged").normalize();
    }

    public Path stagedKnowledgeDir() {
        return privateRoot().resolve("knowledge/staged").normalize();
    }

    public Path quarantineDir() {
        return privateRoot().resolve("quarantine").normalize();
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(stagedAttachmentsDir());
            Files.createDirectories(stagedKnowledgeDir());
            Files.createDirectories(quarantineDir());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建私有存储目录: " + privateRoot(), e);
        }
    }

    public void assertWithinPrivateRoot(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        Path normalized = path.toAbsolutePath().normalize();
        Path root = privateRoot();
        if (!normalized.startsWith(root)) {
            throw new SecurityException("检测到路径逃逸尝试: " + path);
        }
    }

    public Path stageKnowledgeFile(org.springframework.web.multipart.MultipartFile file) throws IOException {
        ensureDirectories();
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') >= 0) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1);
        }
        String fileName = java.util.UUID.randomUUID().toString().replace("-", "") + (ext.isBlank() ? "" : "." + ext);
        Path targetPath = stagedKnowledgeDir().resolve(fileName).normalize();
        assertWithinPrivateRoot(targetPath);
        try (java.io.InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return targetPath;
    }
}
