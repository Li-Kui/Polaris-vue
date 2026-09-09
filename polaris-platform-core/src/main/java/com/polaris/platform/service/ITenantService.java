package com.polaris.platform.service;

import com.polaris.platform.domain.Tenant;

import java.util.List;

/**
 * 中台租户服务层接口
 *
 * @author polaris
 */
public interface ITenantService {

    List<Tenant> selectTenantList(Tenant tenant);

    Tenant selectTenantById(Long tenantId);

    Tenant selectTenantByCode(String tenantCode);

    Tenant createTenant(Tenant tenant, String adminUsername, String adminPassword);

    int updateTenant(Tenant tenant);

    int deleteTenant(Long tenantId);
}
