package com.polaris.ai.modelcenter.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.Map;

/** 不包含密文或明文 Credential 的 Provider Connection 管理视图。 */
@Schema(description = "Provider Connection 安全视图")
public record ProviderConnectionVO(
        Long id,
        Long tenantId,
        Long deptId,
        String connectionName,
        String providerCode,
        String protocolCode,
        String networkMode,
        String baseUrl,
        Map<String, Object> extraConfig,
        long revision,
        String status,
        boolean credentialConfigured,
        String createBy,
        Date createTime,
        String updateBy,
        Date updateTime,
        String remark
) {}
