package com.polaris.ai.modelcenter.schema.options;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/** 不可变白名单 Registry，并统一限制 Resolver 输出规模和格式。 */
@Component
public class SchemaOptionsResolverRegistry {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final int MAX_OPTIONS = 500;
    private static final int MAX_OPTION_TEXT_LENGTH = 1024;

    private final Map<String, SchemaOptionsResolver> resolvers;

    public SchemaOptionsResolverRegistry(List<SchemaOptionsResolver> candidates) {
        Map<String, SchemaOptionsResolver> registered = new LinkedHashMap<>();
        for (SchemaOptionsResolver resolver : candidates) {
            if (resolver == null) {
                throw new IllegalStateException("OptionsResolver 不能为空");
            }
            String code = normalize(resolver.code());
            if (!CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalStateException("OptionsResolver code 格式无效");
            }
            if (registered.putIfAbsent(code, resolver) != null) {
                throw new IllegalStateException("重复 OptionsResolver: " + code);
            }
        }
        this.resolvers = Map.copyOf(registered);
    }

    public boolean contains(String code) {
        return code != null && resolvers.containsKey(normalize(code));
    }

    public Set<String> codes() {
        return resolvers.keySet();
    }

    public List<SchemaOption> resolve(
            String resolverCode,
            OptionsResolveContext context) {
        SchemaOptionsResolver resolver = resolvers.get(normalize(resolverCode));
        if (resolver == null) {
            throw new ServiceException("OptionsResolver 不存在或未注册");
        }
        if (context == null) {
            throw new ServiceException("OptionsResolveContext 不能为空");
        }
        List<SchemaOption> options = resolver.resolve(context);
        if (options == null) {
            throw new ServiceException("OptionsResolver 返回结果不能为空");
        }
        if (options.size() > MAX_OPTIONS) {
            throw new ServiceException("OptionsResolver 返回条数不能超过 " + MAX_OPTIONS);
        }
        Set<String> values = new LinkedHashSet<>();
        for (SchemaOption option : options) {
            if (option == null || option.value() == null || option.value().isBlank()
                    || option.label() == null || option.label().isBlank()) {
                throw new ServiceException("OptionsResolver 返回了无效选项");
            }
            if (option.value().length() > MAX_OPTION_TEXT_LENGTH
                    || option.label().length() > MAX_OPTION_TEXT_LENGTH) {
                throw new ServiceException("OptionsResolver 选项文本不能超过 1024 字符");
            }
            if (!values.add(option.value())) {
                throw new ServiceException("OptionsResolver 返回了重复 value");
            }
        }
        return List.copyOf(options);
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
