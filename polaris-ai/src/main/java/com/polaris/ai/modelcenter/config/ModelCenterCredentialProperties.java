package com.polaris.ai.modelcenter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Model Center 凭据加密密钥配置。生产环境应从环境或外部密钥系统注入。 */
@Data
@Component
@ConfigurationProperties(prefix = "model-center.credential")
public class ModelCenterCredentialProperties {

    /** 当前写入使用的密钥标识。 */
    private String activeKeyId = "default";

    /** 当前写入使用的 256-bit Base64 密钥或至少 32 字符的高强度平台密钥。 */
    private String activeKey;

    /** 历史解密密钥，key 为信封中的 kid，value 与 activeKey 格式相同。 */
    private Map<String, String> decryptionKeys = new LinkedHashMap<>();
}
