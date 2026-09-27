package com.polaris.ai.modelcenter.credential;

import com.polaris.ai.domain.AiProviderConnection;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** 绑定 Credential 密文与 Connection 传输身份的 AEAD 附加数据。 */
public record ProviderCredentialContext(
        Long connectionId,
        Long tenantId,
        String providerCode,
        String protocolCode,
        String normalizedBaseUrl) {

    public ProviderCredentialContext {
        Objects.requireNonNull(connectionId, "connectionId");
        providerCode = normalizeCode(providerCode, "providerCode");
        protocolCode = normalizeCode(protocolCode, "protocolCode");
        normalizedBaseUrl = Objects.requireNonNull(normalizedBaseUrl, "normalizedBaseUrl");
    }

    public static ProviderCredentialContext from(AiProviderConnection connection) {
        Objects.requireNonNull(connection, "connection");
        return new ProviderCredentialContext(
                connection.getId(), connection.getTenantId(),
                connection.getProviderCode(), connection.getProtocolCode(),
                connection.getBaseUrl());
    }

    public byte[] aad() {
        String value = connectionId + "\n"
                + (tenantId == null ? "GLOBAL" : "T:" + tenantId) + "\n"
                + providerCode + "\n" + protocolCode + "\n" + normalizedBaseUrl;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizeCode(String value, String name) {
        return Objects.requireNonNull(value, name).trim().toUpperCase(Locale.ROOT);
    }
}
