package com.polaris.platform.mapper;

import com.polaris.platform.domain.CustomerServiceOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerServiceOperationMapper {
    CustomerServiceOperation selectRequest(@Param("tenantId") Long tenantId,
            @Param("sessionId") String sessionId, @Param("requestId") String requestId);
    List<CustomerServiceOperation> selectSession(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);
    int countSession(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);
    int insert(CustomerServiceOperation operation);
}
