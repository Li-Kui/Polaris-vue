package com.polaris.ai.tools;

import com.polaris.common.core.domain.entity.SysDept;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.system.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
class SysUserToolsTest {

    @Test
    void returnsBoundedSummariesWithMaskedContactDetails() {
        List<SysUser> users = new ArrayList<>();
        for (int index = 1; index <= 60; index++) {
            SysUser user = new SysUser();
            user.setUserId((long) index);
            user.setUserName("user" + index);
            user.setNickName("用户" + index);
            user.setStatus("0");
            user.setPhonenumber("13812345678");
            user.setEmail("person@example.com");
            SysDept department = new SysDept();
            department.setDeptName("研发部");
            user.setDept(department);
            users.add(user);
        }
        ISysUserService userService = service(ISysUserService.class,
                (method, arguments) -> "selectUserList".equals(method) ? users : null);
        SysUserTools tools = new SysUserTools();
        ReflectionTestUtils.setField(tools, "userService", userService);

        List<SysUserTools.UserSummary> result =
                tools.queryUserList(null, null, 100);

        assertEquals(50, result.size());
        assertEquals("138****5678", result.get(0).phone());
        assertEquals("p***@example.com", result.get(0).email());
        assertEquals("研发部", result.get(0).departmentName());
        assertEquals("正常", result.get(0).status());
    }

    @SuppressWarnings("unchecked")
    private <T> T service(Class<T> type, ServiceCall call) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> call.invoke(method.getName(), arguments));
    }

    @FunctionalInterface
    private interface ServiceCall {
        Object invoke(String method, Object[] arguments);
    }
}
