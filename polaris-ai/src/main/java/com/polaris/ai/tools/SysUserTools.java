package com.polaris.ai.tools;

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
    @Tool("根据条件检索系统中的用户列表，返回包含用户ID、账号、昵称、邮箱、手机号等信息的列表。如果不输入任何参数，则默认查询所有用户。")
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
}
