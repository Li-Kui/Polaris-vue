package com.polaris.ai.image;

import com.polaris.common.config.PolarisConfig;
import com.polaris.common.utils.file.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 绘图结果转存工具：将厂商返回的临时图片 URL 下载并落盘到本系统 /profile 目录，
 * 规避厂商 URL 限时过期导致的历史死链问题。
 *
 * @author polaris
 */
@Slf4j
@Component
public class ImageStorageHelper {

    /** 单张图片最大转存体积（20MB），超出则放弃转存、回退原始 URL */
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    /**
     * 下载远程图片并转存到本地 /profile/upload/ai_image 目录
     *
     * @param remoteUrl 厂商返回的临时图片 URL
     * @return 转存成功返回本地相对路径（/profile/...）；失败返回 null，由调用方回退原始 URL
     */
    public String transferToLocal(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            return null;
        }
        // 已是本地资源则无需转存
        if (remoteUrl.contains("/profile/")) {
            return remoteUrl;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(remoteUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                log.warn(">>> [ImageStorageHelper] 下载图片失败, HTTP {}, url={}", code, remoteUrl);
                return null;
            }

            byte[] data;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) {
                        log.warn(">>> [ImageStorageHelper] 图片超过 {} 字节上限，放弃转存, url={}", MAX_BYTES, remoteUrl);
                        return null;
                    }
                    bos.write(buf, 0, n);
                }
                data = bos.toByteArray();
            }

            if (data.length == 0) {
                return null;
            }

            // 写入 /profile/upload/ai_image，writeBytes 按字节魔数自动判扩展名并返回 /profile/... 相对路径
            String uploadDir = PolarisConfig.getProfile() + "/upload/ai_image";
            String relativePath = FileUtils.writeBytes(data, uploadDir);
            log.info(">>> [ImageStorageHelper] 图片转存成功, {} bytes -> {}", data.length, relativePath);
            return relativePath;
        } catch (Exception e) {
            log.error(">>> [ImageStorageHelper] 图片转存异常, url={}", remoteUrl, e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
