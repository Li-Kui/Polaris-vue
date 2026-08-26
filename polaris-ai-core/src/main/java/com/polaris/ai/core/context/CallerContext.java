package com.polaris.ai.core.context;

/**
 * 调用者上下文抽象接口。
 * 统一三种模式（管理后台 / 中台控制台 / API Key）的调用者身份信息。
 */
public interface CallerContext {

    /** 用户ID（管理后台模式） */
    Long getUserId();

    /** 租户ID（中台模式，管理后台为 null） */
    String getTenantId();

    /** 用户名 */
    String getUsername();

    /** 部门ID（管理后台模式，中台为 null） */
    Long getDeptId();

    /** 是否超管（超管看全部数据） */
    boolean isSuperAdmin();

    /** 是否为中台模式 */
    default boolean isPlatformMode() {
        return getTenantId() != null;
    }

    /** 调用方是否为当前租户管理员。 */
    default boolean isTenantAdmin() {
        return false;
    }

    /** API 密钥和服务主体使用的可选细粒度权限检查。 */
    default boolean hasPermission(String permission) {
        return isSuperAdmin();
    }
}
