package com.polaris.common.config.serializer;

import com.polaris.common.annotation.Sensitive;
import com.polaris.common.core.domain.model.LoginUser;
import com.polaris.common.enums.DesensitizedType;
import com.polaris.common.utils.SecurityUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Objects;

/**
 * Jackson 3 数据脱敏序列化过滤
 *
 * @author polaris
 */
public class SensitiveJackson3Serializer extends StdSerializer<String>
{
    private final DesensitizedType desensitizedType;

    public SensitiveJackson3Serializer()
    {
        super(String.class);
        this.desensitizedType = null;
    }

    public SensitiveJackson3Serializer(DesensitizedType desensitizedType)
    {
        super(String.class);
        this.desensitizedType = desensitizedType;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext context) throws JacksonException
    {
        if (desensitizedType != null && desensitization())
        {
            gen.writeString(desensitizedType.desensitizer().apply(value));
        }
        else
        {
            gen.writeString(value);
        }
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property)
    {
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (Objects.nonNull(annotation) && Objects.equals(String.class, property.getType().getRawClass()))
        {
            return new SensitiveJackson3Serializer(annotation.desensitizedType());
        }
        return context.findValueSerializer(property.getType());
    }

    /**
     * 是否需要脱敏处理
     */
    private boolean desensitization()
    {
        try
        {
            LoginUser securityUser = SecurityUtils.getLoginUser();
            // 管理员不脱敏
            return !securityUser.getUser().isAdmin();
        }
        catch (Exception e)
        {
            return true;
        }
    }
}
