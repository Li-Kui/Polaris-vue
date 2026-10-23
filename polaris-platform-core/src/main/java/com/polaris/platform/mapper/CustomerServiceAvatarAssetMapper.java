package com.polaris.platform.mapper;

import com.polaris.platform.domain.CustomerServiceAvatarAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerServiceAvatarAssetMapper {
    Long lockTenant(@Param("tenantId") Long tenantId);
    List<CustomerServiceAvatarAsset> lockAssets(@Param("tenantId") Long tenantId);
    List<CustomerServiceAvatarAsset> list(@Param("tenantId") Long tenantId);
    CustomerServiceAvatarAsset select(@Param("tenantId") Long tenantId, @Param("id") String id);
    int insert(CustomerServiceAvatarAsset asset);
}
