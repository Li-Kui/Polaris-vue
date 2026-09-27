package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.core.context.CallerUtils;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.ai.modelcenter.service.ModelDefaultInternalService;
import com.polaris.ai.runtime.openapi.OpenApiModelDescriptor;
import com.polaris.ai.runtime.openapi.OpenApiModelHandle;
import com.polaris.ai.runtime.openapi.OpenApiModelRuntimeGateway;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 权限感知的 OpenAPI model_code 查询与 Runtime 创建。 */
@Service
public class DefaultOpenApiModelRuntimeGateway
        implements OpenApiModelRuntimeGateway {

    private static final String CAPABILITY = "CHAT_COMPLETION";

    private final AiModelConfigMapper modelMapper;
    private final ModelDefinitionResolver definitionResolver;
    private final ModelDefaultInternalService defaultService;
    private final ChatRuntimeService chatRuntimeService;

    public DefaultOpenApiModelRuntimeGateway(
            AiModelConfigMapper modelMapper,
            ModelDefinitionResolver definitionResolver,
            ModelDefaultInternalService defaultService,
            ChatRuntimeService chatRuntimeService) {
        this.modelMapper = modelMapper;
        this.definitionResolver = definitionResolver;
        this.defaultService = defaultService;
        this.chatRuntimeService = chatRuntimeService;
    }

    @Override
    public List<OpenApiModelDescriptor> listAccessibleChatModels() {
        List<OpenApiModelDescriptor> result = new ArrayList<>();
        for (AiModelConfig model : accessibleModels()) {
            if (model.getModelCode() == null || model.getModelCode().isBlank()) {
                continue;
            }
            try {
                definitionResolver.resolve(model.getId(), CAPABILITY);
                result.add(descriptor(model));
            } catch (RuntimeException ignored) {
                // 列表只返回当前可实际执行的模型与连接。
            }
        }
        return List.copyOf(result);
    }

    @Override
    public OpenApiModelHandle resolve(String modelCode) {
        AiModelConfig model;
        if (modelCode == null || modelCode.isBlank()) {
            Long defaultId = defaultService.resolveModelId(CAPABILITY);
            model = accessibleModels().stream()
                    .filter(item -> Objects.equals(item.getId(), defaultId))
                    .findFirst()
                    .orElseThrow(() -> new ServiceException(
                            "OPENAPI_DEFAULT_MODEL_NOT_ACCESSIBLE"));
        } else {
            String requested = modelCode.trim();
            List<AiModelConfig> matches = accessibleModels().stream()
                    .filter(item -> requested.equals(item.getModelCode()))
                    .toList();
            if (matches.size() != 1) {
                throw new ServiceException("OPENAPI_MODEL_NOT_FOUND: " + requested);
            }
            model = matches.get(0);
        }
        definitionResolver.resolve(model.getId(), CAPABILITY);
        return new OpenApiModelHandle(
                descriptor(model),
                new RuntimeStreamingChatModel(
                        chatRuntimeService, model.getId(), Set.of(), List.of()));
    }

    private List<AiModelConfig> accessibleModels() {
        return modelMapper.selectAvailableModelConfigsByCapability(
                CAPABILITY, CallerUtils.getDeptId(), CallerUtils.isSuperAdmin());
    }

    private OpenApiModelDescriptor descriptor(AiModelConfig model) {
        return new OpenApiModelDescriptor(
                model.getModelCode(), model.getName(),
                model.getRevision() == null ? 1L : model.getRevision());
    }
}
