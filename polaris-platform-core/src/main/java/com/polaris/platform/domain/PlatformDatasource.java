package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部数据源实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "中台外部数据源")
public class PlatformDatasource extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "数据源 ID")
    private Long id;

    @Schema(description = "所属租户 ID")
    private Long tenantId;

    @Schema(description = "数据源名称")
    private String dsName;

    /** MYSQL|POSTGRESQL|SQLSERVER */
    @Schema(description = "数据源类型", allowableValues = {"MYSQL", "POSTGRESQL", "SQLSERVER"})
    private String dsType;

    @Schema(description = "当前连接版本 ID")
    private Long currentVersionId;

    @Schema(description = "当前连接版本号")
    private Integer configVersion;

    @Schema(description = "数据库主机")
    private String host;

    @Schema(description = "数据库端口")
    private Integer port;

    @Schema(description = "数据库名称")
    private String databaseName;

    @Schema(description = "数据库用户名")
    private String username;

    /** 加密信封，仅供内部连接使用。 */
    @JsonIgnore
    @Schema(hidden = true)
    private String password;

    @Schema(description = "是否启用标准 SSL")
    private Boolean sslEnabled;

    @Schema(description = "连接超时时间，单位秒")
    private Integer connectTimeoutSeconds;

    @Schema(description = "查询超时时间，单位秒")
    private Integer queryTimeoutSeconds;

    @Schema(description = "验证状态")
    private String verificationStatus;

    @Schema(description = "数据库产品名称")
    private String databaseProductName;

    @Schema(description = "数据库产品版本")
    private String databaseProductVersion;

    @Schema(description = "最近测试时间")
    private java.util.Date lastTestTime;

    @Schema(description = "最近测试耗时，单位毫秒")
    private Long lastTestLatencyMs;

    @Schema(description = "最近测试错误码")
    private String lastTestErrorCode;

    @Schema(description = "数据源状态，0 表示正常")
    private String status;
}
