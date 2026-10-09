package com.polaris.ai.runtime.openapi;

import java.util.List;

/** Platform OpenAPI 到 Model Center 的无反向模块依赖端口。 */
public interface OpenApiModelRuntimeGateway {

    List<OpenApiModelDescriptor> listAccessibleChatModels();

    OpenApiModelHandle resolve(String modelCode);
}
