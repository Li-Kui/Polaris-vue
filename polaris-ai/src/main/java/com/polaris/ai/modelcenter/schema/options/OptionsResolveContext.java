package com.polaris.ai.modelcenter.schema.options;

/** 由后端权限感知 Connection 查询构造，不能直接信任客户端同名字段。 */
public record OptionsResolveContext(
        Long connectionId,
        long connectionRevision,
        String providerCode,
        String protocolCode,
        String modelName,
        String capability,
        String field) {}
