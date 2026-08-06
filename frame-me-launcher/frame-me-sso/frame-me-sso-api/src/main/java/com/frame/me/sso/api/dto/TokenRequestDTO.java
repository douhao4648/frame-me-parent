package com.frame.me.sso.api.dto;

import lombok.Data;

/**
 * 换 token 请求 DTO.
 *
 * @author frame-me
 */
@Data
public class TokenRequestDTO {

    /** 授权码. */
    private String code;

    /** 应用 ID. */
    private String appId;

    /** 应用密钥（INTERNAL 免）. */
    private String appSecret;

    /** 回调地址. */
    private String redirectUri;

    /** 防重放（可选）. */
    private String nonce;
}
