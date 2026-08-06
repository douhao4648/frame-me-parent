package com.frame.me.sso.api.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * Token 响应 VO.
 *
 * @author frame-me
 */
@Data
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** sa-token 不透明 token（下游凭此调 /userinfo）. */
    private String accessToken;
}
