package com.polaris.platform.auth;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 工作流分享 iframe 来源校验。
 */
public final class WorkflowShareOriginPolicy {

    private WorkflowShareOriginPolicy() {
    }

    public static boolean isSafeOrigin(String origin) {
        if (origin == null || !origin.matches("https?://[A-Za-z0-9._:\\[\\]-]+")) {
            return false;
        }
        try {
            URI uri = new URI(origin).parseServerAuthority();
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                return false;
            }
            int port = uri.getPort();
            if (port < 0) {
                return uri.getRawAuthority().equals(uri.getHost());
            }
            return port <= 65535;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
