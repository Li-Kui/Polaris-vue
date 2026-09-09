package com.polaris.ai.tools;

import com.github.pagehelper.PageHelper;
import com.polaris.ai.tools.base.*;
import com.polaris.ai.utils.ToolSseHolder;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.system.service.ISysUserService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 工具类 —— 提供系统用户查询功能
 *
 * @author polaris
 */
@Component
@AiAgentTool(value = "系统用户审计", scope = ToolScope.ADMIN_ONLY)
public class SysUserTools implements AiTool {
    @Autowired
    private ISysUserService userService;

    /**
     * 查询系统中的用户列表
     *
     * @param userName    要检索的登录账号（可选，支持模糊查询）
     * @param phonenumber 要检索的手机号码（可选，支持模糊查询）
     * @return 用户列表
     */
    @Tool("根据条件检索或分析系统用户。返回账号、昵称、部门、状态及脱敏联系方式，单次最多50条。")
    @AiToolPermission(value = "system:user:list", sideEffect = ToolSideEffect.READ)
    public List<UserSummary> queryUserList(
            @P("要检索的登录账号，支持模糊查询，非必填") String userName,
            @P("要检索的手机号码，支持模糊查询，非必填") String phonenumber,
            @P("最多返回多少条，范围1到50，非必填，默认20") Integer maxResults
    ) {
        ToolSseHolder.ensureActive();
        SysUser queryUser = new SysUser();
        queryUser.setUserName(userName);
        queryUser.setPhonenumber(phonenumber);
        int limit = maxResults == null ? 20 : Math.max(1, Math.min(maxResults, 50));
        List<SysUser> userList;
        PageHelper.startPage(1, limit, false);
        try {
            userList = userService.selectUserList(queryUser);
        } finally {
            PageHelper.clearPage();
        }
        ToolSseHolder.ensureActive();
        return userList.stream()
                .limit(limit)
                .map(user -> new UserSummary(
                        user.getUserId(), user.getUserName(), user.getNickName(),
                        user.getDept() == null ? null : user.getDept().getDeptName(),
                        "0".equals(user.getStatus()) ? "正常" : "停用",
                        maskPhone(user.getPhonenumber()), maskEmail(user.getEmail())))
                .toList();
    }

    private static String maskPhone(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 7) return "***";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String maskEmail(String value) {
        if (value == null || value.isBlank()) return null;
        int separator = value.indexOf('@');
        if (separator <= 0) return "***";
        return value.substring(0, 1) + "***" + value.substring(separator);
    }

    public record UserSummary(
            Long userId,
            String userName,
            String nickName,
            String departmentName,
            String status,
            String phone,
            String email) {
    }

    /**
     * 新增系统用户
     *
     * @param userName    登录账号（必填）
     * @param nickName    用户昵称（必填）
     * @param password    登录密码（必填）
     * @param phonenumber 手机号码（可选）
     * @param email       邮箱账号（可选）
     * @param sex         用户性别（可选，'0'代表男，'1'代表女，'2'代表未知）
     * @return 新增结果提示
     */
    @Tool("在系统中新增一个系统用户，支持设置账号、昵称、密码、手机号、邮箱和性别。所有输入参数均需以明确的文字提供。")
    @AiToolPermission(value = "system:user:add", sideEffect = ToolSideEffect.WRITE)
    public String createUser(
            @P("登录账号，必填，必须唯一") String userName,
            @P("用户昵称，必填") String nickName,
            @P("登录密码，必填") String password,
            @P("手机号码，可选") String phonenumber,
            @P("电子邮箱，可选") String email,
            @P("用户性别，可选，'0'代表男，'1'代表女，'2'代表未知") String sex
    ) {
        ToolSseHolder.ensureActive();
        if (userName == null || userName.trim().isEmpty()) {
            return "新增用户失败：登录账号不能为空";
        }
        if (nickName == null || nickName.trim().isEmpty()) {
            return "新增用户失败：用户昵称不能为空";
        }
        if (password == null || password.trim().isEmpty()) {
            return "新增用户失败：登录密码不能为空";
        }

        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setPassword(password);
        user.setPhonenumber(phonenumber);
        user.setEmail(email);
        user.setSex(sex == null || sex.trim().isEmpty() ? "0" : sex);

        user.setCreateBy(com.polaris.common.utils.SecurityUtils.getUsername());

        // 统一调用封装的 Service 业务方法，进行唯一性校验、密码加密和数据库保存
        ToolSseHolder.ensureActive();
        String errorMsg = userService.insertUserWithCheck(user);
        if (com.polaris.common.utils.StringUtils.isNotEmpty(errorMsg)) {
            return errorMsg;
        }

        return "成功新增系统用户，账号: " + userName + ", 昵称: " + nickName;
    }
}
