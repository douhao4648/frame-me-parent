package com.frame.me.encrypt;

/**
 * 配置加密模块常量.
 */
public interface EncryptConstant {

    /** 主密码配置键. */
    String PASSWORD_KEY = "me.encrypt.password";

    /** 加密算法配置键. */
    String ALGORITHM_KEY = "me.encrypt.algorithm";

    /** 密钥迭代次数配置键. */
    String ITERATIONS_KEY = "me.encrypt.iterations";

    /** 密文前缀配置键. */
    String PREFIX_KEY = "me.encrypt.prefix";

    /** 密文后缀配置键. */
    String SUFFIX_KEY = "me.encrypt.suffix";

    /** 默认加密算法（PBE + HMAC-SHA512 + AES-256，需要 IV）. */
    String DEFAULT_ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";

    /** 默认密钥迭代次数. */
    int DEFAULT_ITERATIONS = 1000;

    /** 默认密文前缀. */
    String DEFAULT_PREFIX = "ME(";

    /** 默认密文后缀. */
    String DEFAULT_SUFFIX = ")";
}
