package com.polaris.platform.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.platform.domain.CustomerServiceChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerServiceChannelMapper {
    List<CustomerServiceChannel> selectApp(@Param("tenantId") Long tenantId, @Param("appId") Long appId);
    CustomerServiceChannel select(@Param("tenantId") Long tenantId, @Param("id") String id);
    CustomerServiceChannel lock(@Param("tenantId") Long tenantId, @Param("id") String id);
    CustomerServiceChannel selectCode(@Param("tenantId") Long tenantId, @Param("code") String code);
    /** 仅公开 HTML 容器查找来源策略，不返回知识、模型或会话；运行接口仍必须限定真实租户。 */
    @InterceptorIgnore(tenantLine = "true")
    CustomerServiceChannel selectEmbed(@Param("code") String code);
    int insert(CustomerServiceChannel channel);
    int update(CustomerServiceChannel channel);
}
