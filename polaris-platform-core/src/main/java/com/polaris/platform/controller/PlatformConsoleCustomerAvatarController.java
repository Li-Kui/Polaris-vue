package com.polaris.platform.controller;

import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.service.CustomerServiceAvatarAssetService;
import com.polaris.platform.service.CustomerServiceConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/** 人物素材不暴露静态路径、查询串凭据或跨租户授权；浏览器用已鉴权请求创建本地Blob。 */
@RestController
@RequestMapping("/platform/console/customer-services")
public class PlatformConsoleCustomerAvatarController {
    private final CustomerServiceAvatarAssetService assets;
    private final CustomerServiceConversationService conversations;
    public PlatformConsoleCustomerAvatarController(CustomerServiceAvatarAssetService assets, CustomerServiceConversationService conversations) {
        this.assets = assets; this.conversations = conversations;
    }
    @GetMapping("/avatar-assets")
    public ResponseEntity<?> list() { return ResponseEntity.ok().header("Cache-Control", "no-store").body(ResultData.ok(assets.list())); }

    @PostMapping(value = "/avatar-assets", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> upload(@RequestParam String name, HttpServletRequest request) throws IOException {
        if (request.getParameterMap().size() != 1 || request.getParameterValues("name") == null || request.getParameterValues("name").length != 1) throw new ServiceException("INVALID_INPUT: 素材接口不接受额外参数", 400);
        byte[] bytes = request.getInputStream().readNBytes(CustomerServiceAvatarAssetService.MAX_BYTES + 1);
        if (bytes.length > CustomerServiceAvatarAssetService.MAX_BYTES) throw new ServiceException("INVALID_INPUT: 人物素材不能超过8MiB", 413);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ResultData.ok(assets.upload(name, bytes)));
    }
    @GetMapping("/avatar-assets/{id}")
    public ResponseEntity<byte[]> preview(@PathVariable String id) { return media(assets.read(id)); }

    @GetMapping("/sessions/{sessionId}/avatar-assets/{id}")
    public ResponseEntity<byte[]> sessionAsset(@PathVariable String sessionId, @PathVariable String id) {
        requireReference(sessionId, id);
        var data = assets.read(id);
        requireReference(sessionId, id); // 文件读取期间若停用／关闭／渠道变更，不再返回内容。
        return media(data);
    }
    private void requireReference(String sessionId, String id) {
        var config = conversations.avatar(sessionId);
        if (config == null || !config.assetIds().contains(id)) throw new ServiceException("ASSET_UNAVAILABLE: 此发布未引用该人物素材", 404);
    }
    private ResponseEntity<byte[]> media(CustomerServiceAvatarAssetService.AssetData data) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .contentType(MediaType.parseMediaType(data.contentType())).body(data.bytes());
    }
}
