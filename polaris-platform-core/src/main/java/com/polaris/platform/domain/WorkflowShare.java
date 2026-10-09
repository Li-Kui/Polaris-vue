package com.polaris.platform.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

import java.util.Date;

/**
 * 工作流分享实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工作流分享")
public class WorkflowShare extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "分享短码")
    private String shareCode;

    @Schema(description = "分享名称")
    private String shareName;

    @Schema(description = "工作流定义 ID")
    private Long workflowDefinitionId;

    @Schema(description = "页面类型覆盖")
    private String pageType;

    @Schema(description = "页面配置覆盖")
    private String pageConfigJson;

    @Schema(description = "允许嵌入的 Origin 列表")
    private String allowedOrigins;

    @Schema(description = "每分钟启动执行请求次数限制；读取和上传使用独立防刷限制")
    private Integer rateLimit;

    @Schema(description = "状态（0正常 1停用）")
    private String status;

    @Schema(description = "过期时间")
    private Date expireTime;

    /** 区分更新请求中省略字段与显式清空，不作为数据库列或响应字段。 */
    @JsonIgnore
    @Schema(hidden = true)
    @Setter(AccessLevel.NONE)
    private boolean pageTypeSpecified;

    @JsonIgnore
    @Schema(hidden = true)
    @Setter(AccessLevel.NONE)
    private boolean expireTimeSpecified;

    public void setPageType(String pageType) {
        this.pageType = pageType;
        this.pageTypeSpecified = true;
    }

    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
        this.expireTimeSpecified = true;
    }
}
