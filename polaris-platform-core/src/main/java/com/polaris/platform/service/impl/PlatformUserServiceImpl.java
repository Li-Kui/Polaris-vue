package com.polaris.platform.service.impl;

import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.mapper.PlatformUserMapper;
import com.polaris.platform.service.IPlatformAuthService;
import com.polaris.platform.service.IPlatformUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 中台租户用户服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformUserServiceImpl implements IPlatformUserService {

    @Autowired
    private PlatformUserMapper platformUserMapper;

    @Autowired
    private IPlatformAuthService platformAuthService;

    /**
     * 查询指定租户的用户列表
     */
    @Override
    public List<PlatformUser> selectUserListByTenantId(Long tenantId) {
        return platformUserMapper.selectByTenantId(tenantId);
    }

    /**
     * 根据 ID 查询中台用户
     */
    @Override
    public PlatformUser selectUserById(Long id) {
        return platformUserMapper.selectById(id);
    }

    /**
     * 新增中台用户
     */
    @Override
    public int insertUser(PlatformUser user) {
        return platformAuthService.createPlatformUser(user);
    }

    /**
     * 修改中台用户
     */
    @Override
    public int updateUser(PlatformUser user) {
        return platformUserMapper.update(user);
    }

    /**
     * 删除中台用户
     */
    @Override
    public int deleteUserById(Long id) {
        return platformUserMapper.deleteById(id);
    }
}
