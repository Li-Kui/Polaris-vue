package com.polaris.platform.service;

/**
 * 中台用户认证异常
 *
 * @author polaris
 */
public class PlatformAuthenticationException extends RuntimeException {

    public PlatformAuthenticationException() {
        super("租户编码、用户名或密码错误");
    }
}
