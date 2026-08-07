package com.frame.me.sso.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 换 token 请求 DTO.
 *
 * <p>{@code grantType} 区分两种授权模式（缺省按 {@code authorization_code} 处理）：
 * {@code authorization_code} 需 code + redirectUri；{@code client_credentials} 仅需
 * appId + appSecret（机器对机器，无用户维度）。必填项随模式变化，在服务端按模式校验.</p>
 *
 * @author frame-me
 */
@Data
public class TokenRequestDTO {

    /** 授权模式：authorization_code（默认）/ client_credentials. */
    private String grantType;

    /** 授权码（authorization_code 必填）. */
    private String code;

    /** 应用 ID. */
    @NotBlank
    private String appId;

    /** 应用密钥（INTERNAL / EXTERNAL 均必填）. */
    private String appSecret;

    /** 回调地址（authorization_code 必填）. */
    private String redirectUri;

    /** 防重放（可选）. */
    private String nonce;
}
