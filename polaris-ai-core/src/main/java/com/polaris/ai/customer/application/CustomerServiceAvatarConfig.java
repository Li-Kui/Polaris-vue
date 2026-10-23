package com.polaris.ai.customer.application;

import com.polaris.common.exception.ServiceException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** 仅引用本租户不可变素材；不接受远程 URL、HTML 或人物驱动脚本。 */
public record CustomerServiceAvatarConfig(String name, String coverAssetId, String idleAssetId,
        String listeningAssetId, String thinkingAssetId, String speakingAssetId, String fit) {
    public CustomerServiceAvatarConfig {
        if (name == null || name.isBlank() || name.length() > 64 || coverAssetId == null) throw invalid();
        name = name.strip();
        for (String id : Stream.of(coverAssetId, idleAssetId, listeningAssetId, thinkingAssetId, speakingAssetId)
                .filter(Objects::nonNull).toList()) {
            if (!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw invalid();
        }
        fit = fit == null ? "CONTAIN" : fit;
        if (!List.of("CONTAIN", "COVER").contains(fit)) throw invalid();
    }
    public List<String> assetIds() {
        return Stream.of(coverAssetId, idleAssetId, listeningAssetId, thinkingAssetId, speakingAssetId)
                .filter(Objects::nonNull).distinct().toList();
    }
    private static ServiceException invalid() { return new ServiceException("INVALID_INPUT: 请填写人物名称、静态封面和有效的素材引用", 400); }
}
