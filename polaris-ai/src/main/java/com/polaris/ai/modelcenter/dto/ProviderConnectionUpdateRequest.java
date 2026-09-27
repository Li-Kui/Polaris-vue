package com.polaris.ai.modelcenter.dto;

import com.polaris.ai.modelcenter.credential.CredentialAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 修改 Provider Connection 可变字段的请求。 */
@Schema(description = "修改 Provider Connection 请求")
public record ProviderConnectionUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String connectionName,

        @NotNull
        CredentialAction credentialAction,

        @Size(max = 32)
        @Schema(description = "仅在 credentialAction=REPLACE 时使用")
        Map<String, Object> credential,

        @NotNull
        @Positive
        Long expectedRevision,

        @Size(max = 500)
        String remark
) {
    @Override
    public String toString() {
        return "ProviderConnectionUpdateRequest[connectionName=" + connectionName
                + ", credentialAction=" + credentialAction
                + ", credential=<redacted>"
                + ", expectedRevision=" + expectedRevision
                + ", remark=" + remark + "]";
    }
}
