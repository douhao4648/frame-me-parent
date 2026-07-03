package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * JWT 当前用户解析器.
 *
 * @author frame-me
 */
@RequiredArgsConstructor
public class JwtAuthUserResolver implements IAuthUserResolver {

    private final JwtAuthProperties properties;
    private final IAuthService authService;

    @Override
    public User resolve(HttpServletRequest request) {
        String header = request.getHeader(properties.getTokenHeader());
        if (header == null || !header.startsWith(properties.getTokenPrefix())) {
            return null;
        }
        String token = header.substring(properties.getTokenPrefix().length()).trim();
        return authService.getUser(token);
    }
}
