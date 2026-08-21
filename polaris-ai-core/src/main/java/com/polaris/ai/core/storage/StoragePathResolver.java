package com.polaris.ai.core.storage;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 租户感知的文件存储路径解析器。
 * 根据 CallerContext 自动按租户隔离存储路径。
 */
@Component
public class StoragePathResolver {

    @Value("${polaris.profile:./profile}")
    private String uploadDir;

    /**
     * 解析知识库文档存储路径
     */
    public Path resolveKnowledgePath(String fileName) {
        return resolve("ai_knowledge", fileName);
    }

    /**
     * 解析图像存储路径
     */
    public Path resolveImagePath(String fileName) {
        return resolve("ai_image", fileName);
    }

    /**
     * 解析通用存储路径
     */
    public Path resolve(String category, String fileName) {
        return Paths.get(uploadDir, "upload", category, resolveTenantPrefix(), fileName);
    }

    private String resolveTenantPrefix() {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx != null && ctx.getTenantId() != null) {
            return "tenant_" + ctx.getTenantId();
        }
        return "admin";
    }
}
