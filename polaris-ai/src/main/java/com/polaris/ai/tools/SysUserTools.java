package com.polaris.ai.tools;

import com.polaris.ai.tools.base.AiAgentTool;
import com.polaris.ai.tools.base.AiTool;
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
@AiAgentTool("系统用户审计")
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
    @Tool("根据条件检索或分析系统中的用户列表与用户数据（支持生成用户分析报告、统计用户数量、查看账号、昵称、邮箱、手机号等）。如果不输入任何参数，则默认获取全量用户数据以供统计与分析。")
    public List<SysUser> queryUserList(
            @P("要检索的登录账号，支持模糊查询，非必填") String userName,
            @P("要检索的手机号码，支持模糊查询，非必填") String phonenumber
    ) {
        SysUser queryUser = new SysUser();
        queryUser.setUserName(userName);
        queryUser.setPhonenumber(phonenumber);
        List<SysUser> userList = userService.selectUserList(queryUser);
        return userList;
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
    public String createUser(
            @P("登录账号，必填，必须唯一") String userName,
            @P("用户昵称，必填") String nickName,
            @P("登录密码，必填") String password,
            @P("手机号码，可选") String phonenumber,
            @P("电子邮箱，可选") String email,
            @P("用户性别，可选，'0'代表男，'1'代表女，'2'代表未知") String sex
    ) {
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

        try {
            user.setCreateBy(com.polaris.common.utils.SecurityUtils.getUsername());
        } catch (Exception e) {
            user.setCreateBy("admin");
        }

        // 统一调用封装的 Service 业务方法，进行唯一性校验、密码加密和数据库保存
        String errorMsg = userService.insertUserWithCheck(user);
        if (com.polaris.common.utils.StringUtils.isNotEmpty(errorMsg)) {
            return errorMsg;
        }

        return "成功新增系统用户，账号: " + userName + ", 昵称: " + nickName;
    }
}
