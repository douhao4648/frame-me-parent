package com.frame.me.encrypt;

/**
 * 配置加密模块常量.
 */
public interface EncryptConstant {

    /** 主密码配置键. */
    String PASSWORD_KEY = "frame.me.encrypt.password";

    /** 加密算法配置键. */
    String ALGORITHM_KEY = "frame.me.encrypt.algorithm";

    /** 密钥迭代次数配置键. */
    String ITERATIONS_KEY = "frame.me.encrypt.iterations";

    /** 密文前缀配置键. */
    String PREFIX_KEY = "frame.me.encrypt.prefix";

    /** 密文后缀配置键. */
    String SUFFIX_KEY = "frame.me.encrypt.suffix";

    /** 默认加密算法（PBE + HMAC-SHA512 + AES-256，需要 IV）. */
    String DEFAULT_ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";

    /** 默认密钥迭代次数. */
    int DEFAULT_ITERATIONS = 1000;

    /** 默认密文前缀. */
    String DEFAULT_PREFIX = "ME(";

    /** 默认密文后缀. */
    String DEFAULT_SUFFIX = ")";
}
