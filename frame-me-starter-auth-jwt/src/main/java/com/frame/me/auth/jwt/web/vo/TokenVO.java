package com.frame.me.auth.jwt.web.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Token 响应 VO.
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
     * Refresh Token.
     */
    private String refreshToken;
}
