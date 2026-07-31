package com.frame.me.encrypt;

/**
 * 配置加密模块常量.
 */
public final class EncryptConstant {

    private EncryptConstant() {
    }

    /** 主密码配置键. */
    public static final String PASSWORD_KEY = "me.encrypt.password";

    /** 加密算法配置键. */
    public static final String ALGORITHM_KEY = "me.encrypt.algorithm";

    /** 密钥迭代次数配置键. */
    public static final String ITERATIONS_KEY = "me.encrypt.iterations";

    /** 密文前缀配置键. */
    public static final String PREFIX_KEY = "me.encrypt.prefix";

    /** 密文后缀配置键. */
    public static final String SUFFIX_KEY = "me.encrypt.suffix";

    /** 默认加密算法（PBE + HMAC-SHA512 + AES-256，需要 IV）. */
    public static final String DEFAULT_ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";

    /** 默认密钥迭代次数.
     *
     * <p>取值 210,000 符合 OWASP 2023 对 PBKDF2-HMAC-SHA512 的推荐值。
     * 可通过 {@code me.encrypt.iterations} 覆盖。</p>
     */
    public static final int DEFAULT_ITERATIONS = 210_000;

    /** 默认密文前缀. */
    public static final String DEFAULT_PREFIX = "ME(";

    /** 默认密文后缀. */
    public static final String DEFAULT_SUFFIX = ")";
}
