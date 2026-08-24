package com.polaris.platform.domain;

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

    @Schema(description = "JDBC 连接地址")
    private String jdbcUrl;

    @Schema(description = "数据库用户名")
    private String username;

    /** AES加密 */
    @Schema(description = "数据库密码", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Schema(description = "查询超时时间")
    private Integer queryTimeout;

    @Schema(description = "是否只读")
    private Boolean readOnly;

    @Schema(description = "扩展配置 JSON")
    private String extraConfig;

    @Schema(description = "数据源状态，0 表示正常")
    private String status;
}
