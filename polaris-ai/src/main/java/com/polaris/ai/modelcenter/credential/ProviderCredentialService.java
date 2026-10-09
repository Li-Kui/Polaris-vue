package com.polaris.ai.modelcenter.credential;

import java.util.Map;

/** Provider Credential 的可替换加密边界。 */
public interface ProviderCredentialService {

    String encrypt(
            Map<String, Object> credential,
            ProviderCredentialContext context);

    Map<String, Object> decrypt(
            String ciphertext,
            ProviderCredentialContext context);

    boolean isConfigured(String ciphertext);
}
