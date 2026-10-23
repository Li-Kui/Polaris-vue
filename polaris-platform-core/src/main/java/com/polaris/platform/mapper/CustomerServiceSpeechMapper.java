package com.polaris.platform.mapper;

import com.polaris.platform.domain.CustomerServiceSpeech;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface CustomerServiceSpeechMapper {
    CustomerServiceSpeech select(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId, @Param("requestId") String requestId);
    int count(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);
    List<CustomerServiceSpeech> selectSession(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);
    int insert(CustomerServiceSpeech speech);
    int finish(CustomerServiceSpeech speech);
    int saveUsage(CustomerServiceSpeech speech);
    int interrupt(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId, @Param("requestId") String requestId, @Param("now") Date now);
    int expire(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId, @Param("now") Date now);
}
