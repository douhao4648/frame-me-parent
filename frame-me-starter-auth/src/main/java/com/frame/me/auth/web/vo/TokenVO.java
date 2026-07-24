package com.frame.me.auth.web.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Token 响应 VO（JWT / Sa-Token 等认证实现共用）.
 *
 * <p>无 Refresh Token 概念的认证实现（如 sa-token 会话模型）将
 * {@link #refreshToken} 置为 {@code null}。</p>
 *
 * @author frame-me
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Access Token.
     */
    private String accessToken;

    /**
     * Refresh Token（实现无此概念时为 {@code null}）.
     */
    private String refreshToken;
}
