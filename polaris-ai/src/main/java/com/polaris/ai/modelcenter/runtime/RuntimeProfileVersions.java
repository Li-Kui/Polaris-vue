package com.polaris.ai.modelcenter.runtime;

/** 参与 Runtime Definition Hash 的可信 Profile 版本。 */
public record RuntimeProfileVersions(
        int protocol,
        int provider,
        Integer model) {

    public RuntimeProfileVersions {
        if (protocol < 1 || provider < 1 || model != null && model < 1) {
            throw new IllegalArgumentException("Runtime Profile 版本无效");
        }
    }
}
