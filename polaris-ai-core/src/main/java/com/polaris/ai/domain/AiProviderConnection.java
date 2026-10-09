package com.polaris.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polaris.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** AI Provider 连接配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider_connection")
public class AiProviderConnection extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deptId;
    private String connectionName;
    private String providerCode;
    private String protocolCode;
    private String networkMode;
    private String baseUrl;

    /** 加密后的凭据 envelope，不允许通过普通 JSON 序列化泄露。 */
    @JsonIgnore
    @ToString.Exclude
    private String credentialCiphertext;

    /** Provider Profile 白名单内的非敏感连接配置 JSON。 */
    private String extraConfig;
    private Long revision;
    private String status;

    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
