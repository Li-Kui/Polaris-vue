package com.polaris.platform.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.platform.domain.PlatformDatasourceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据源连接版本数据访问接口。 */
@Mapper
public interface PlatformDatasourceVersionMapper {
    PlatformDatasourceVersion selectById(@Param("id") Long id);
    @InterceptorIgnore(tenantLine = "true")
    PlatformDatasourceVersion selectByDatasourceAndVersion(
            @Param("datasourceId") Long datasourceId,
            @Param("versionNo") Integer versionNo);
    List<PlatformDatasourceVersion> selectByDatasourceId(@Param("datasourceId") Long datasourceId);
    int selectNextVersionNo(@Param("datasourceId") Long datasourceId);
    int insert(PlatformDatasourceVersion version);
}
