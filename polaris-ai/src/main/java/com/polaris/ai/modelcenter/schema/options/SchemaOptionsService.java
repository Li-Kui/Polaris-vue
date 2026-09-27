package com.polaris.ai.modelcenter.schema.options;

import com.polaris.ai.modelcenter.schema.ResolvedCapabilitySchema;
import com.polaris.ai.modelcenter.schema.SchemaProfileResolver;
import com.polaris.ai.modelcenter.schema.UiFieldDefinition;
import com.polaris.ai.modelcenter.service.IAiProviderConnectionService;
import com.polaris.ai.modelcenter.vo.ProviderConnectionVO;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 从权限感知 Connection 构造可信上下文后调用白名单 OptionsResolver。 */
@Service
public class SchemaOptionsService {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final int MAX_MODEL_NAME_LENGTH = 200;

    private final IAiProviderConnectionService connectionService;
    private final SchemaProfileResolver profileResolver;
    private final SchemaOptionsResolverRegistry resolverRegistry;

    public SchemaOptionsService(
            IAiProviderConnectionService connectionService,
            SchemaProfileResolver profileResolver,
            SchemaOptionsResolverRegistry resolverRegistry) {
        this.connectionService = connectionService;
        this.profileResolver = profileResolver;
        this.resolverRegistry = resolverRegistry;
    }

    public List<SchemaOption> resolve(SchemaOptionsRequest request) {
        validate(request);
        ProviderConnectionVO connection = connectionService.get(request.connectionId());
        if (!"1".equals(connection.status())) {
            throw new ServiceException("Provider Connection 未启用");
        }
        String capability = normalizeCode(request.capability());
        ResolvedCapabilitySchema resolved = profileResolver.resolveLatest(
                capability, connection.protocolCode(), connection.providerCode(),
                request.modelName());
        UiFieldDefinition ui = resolved.definition().uiSchema().get(request.field());
        if (ui == null || ui.optionsResolver() == null
                || ui.optionsResolver().isBlank()) {
            throw new ServiceException("字段未声明动态 OptionsResolver");
        }
        OptionsResolveContext context = new OptionsResolveContext(
                connection.id(), connection.revision(), connection.providerCode(),
                connection.protocolCode(), request.modelName(), capability,
                request.field());
        return resolverRegistry.resolve(ui.optionsResolver(), context);
    }

    public SchemaOptionsCacheKey cacheKey(
            String resolverCode,
            OptionsResolveContext context) {
        return new SchemaOptionsCacheKey(
                normalizeCode(resolverCode), context.connectionId(),
                context.connectionRevision(), context.modelName(),
                normalizeCode(context.capability()), context.field());
    }

    private void validate(SchemaOptionsRequest request) {
        if (request == null || request.connectionId() == null
                || request.connectionId() <= 0) {
            throw new ServiceException("connectionId 必须是正整数");
        }
        if (request.modelName() == null || request.modelName().isBlank()
                || request.modelName().length() > MAX_MODEL_NAME_LENGTH) {
            throw new ServiceException("modelName 格式无效");
        }
        if (!CODE_PATTERN.matcher(normalizeCode(request.capability())).matches()) {
            throw new ServiceException("capability 格式无效");
        }
        if (request.field() == null
                || !FIELD_PATTERN.matcher(request.field()).matches()) {
            throw new ServiceException("field 格式无效");
        }
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
