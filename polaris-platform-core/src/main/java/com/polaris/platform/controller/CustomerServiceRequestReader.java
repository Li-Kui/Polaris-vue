package com.polaris.platform.controller;

import com.polaris.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 客服接口共用的有界严格 JSON 读取，拒绝重复键、未知字段及隐式类型转换。 */
final class CustomerServiceRequestReader {
    private final ObjectMapper json;
    CustomerServiceRequestReader(ObjectMapper json) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    }
    <T> T read(HttpServletRequest request, Class<T> type) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(32769);
            if (bytes.length > 32768) throw new ServiceException("INVALID_INPUT: 请求体超过 32KiB", 413);
            T value = json.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            if (value == null) throw invalid();
            return value;
        } catch (ServiceException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw invalid(); }
    }
    private ServiceException invalid() { return new ServiceException("INVALID_INPUT: 请求字段、格式或长度无效", 400); }
}
