package com.polaris.platform.domain;

import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方 API 连接器实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台第三方 API 连接器")
public class PlatformApiConnector extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "连接器 ID")
    private Long id;

    @Schema(description = "所属租户 ID")
    private Long tenantId;

    @Schema(description = "连接器名称")
    private String connectorName;

    @Schema(description = "连接器基础地址")
    private String baseUrl;

    /** NONE|API_KEY|BEARER|BASIC */
    @Schema(description = "认证类型", allowableValues = {"NONE", "API_KEY", "BEARER", "BASIC"})
    private String authType;

    /** JSON */
    @Schema(description = "认证配置 JSON")
    private String authConfig;

    @Schema(description = "默认请求头 JSON")
    private String defaultHeaders;

    @Schema(description = "请求超时时间，单位毫秒")
    private Integer timeoutMs;

    @Schema(description = "连接器状态，0 表示正常")
    private String status;
}
