package com.polaris.ai.modelcenter.discovery;

/** 远程模型身份信息；不携带、不推断 Capability。 */
public record RemoteModelInfo(
        String id,
        String ownedBy) {
}
