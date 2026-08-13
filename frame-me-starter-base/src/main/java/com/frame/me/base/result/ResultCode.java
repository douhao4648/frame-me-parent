package com.frame.me.base.result;

import lombok.Getter;

/**
 * 统一响应状态码
 */
@Getter
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "请求成功"),

    /**
     * 系统错误
     */
    ERROR(500, "系统错误"),

    /**
     * 参数错误
     */
    BAD_REQUEST(400, "参数错误"),

    /**
     * 未授权（会话缺失/失效：未登录访问、token 过期/被踢/被顶）
     *
     * <p>前端语义:跳登录页重新登录.</p>
     */
    UNAUTHORIZED(401, "未授权"),

    /**
     * 凭证错误（账号/密码/授权码/密钥/refresh token 失效等登录/换 token 流程本身的失败）
     *
     * <p>与会话缺失 {@link #UNAUTHORIZED}(401)区分:401 表示"没有有效会话,需重新登录"
     * (前端跳登录页);4001 表示"登录/换 token 流程本身的凭证有问题"
     * (前端留在登录页显示错误提示,避免循环重定向).</p>
     */
    BAD_CREDENTIAL(4001, "凭证错误"),

    /**
     * 禁止访问
     */
    FORBIDDEN(403, "禁止访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 请求方法不支持
     */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /**
     * 请求超时
     */
    REQUEST_TIMEOUT(408, "请求超时"),

    /**
     * 资源冲突
     */
    CONFLICT(409, "资源冲突"),

    /**
     * 请求过于频繁
     */
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    /**
     * 业务异常
     */
    BUSINESS_ERROR(600, "业务异常");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
