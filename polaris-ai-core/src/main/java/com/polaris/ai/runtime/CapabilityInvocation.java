package com.polaris.ai.runtime;

/** 业务请求保持强类型，Capability Code 仅用于路由。 */
public interface CapabilityInvocation {

    String capabilityCode();
}
