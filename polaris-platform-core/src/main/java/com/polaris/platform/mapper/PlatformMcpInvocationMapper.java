package com.polaris.platform.mapper;

import com.polaris.platform.domain.PlatformMcpInvocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformMcpInvocationMapper {
    int insert(PlatformMcpInvocation invocation);
    int markDispatched(@Param("tenantId") Long tenantId, @Param("invocationId") String invocationId);
    int finish(PlatformMcpInvocation invocation);
    List<PlatformMcpInvocation> selectList(@Param("tenantId") Long tenantId,
            @Param("connectionId") Long connectionId, @Param("toolId") Long toolId, @Param("status") String status);
}
