package com.frame.me.tester.auth;

import com.frame.me.auth.jwt.core.IAuthUserDetailsService;
import com.frame.me.auth.jwt.util.PasswordUtils;
import com.frame.me.base.user.User;
import org.springframework.stereotype.Service;

/**
 * 示例用户详情服务.
 *
 * <p>仅用于演示，实际项目应从数据库查询用户。</p>
 *
 * @author frame-me
 */
@Service
public class DemoAuthUserDetailsService implements IAuthUserDetailsService {

    /**
     * 示例加密密码：明文为 {@code 123456}.
     */
    private static final String DEMO_ENCODED_PASSWORD = PasswordUtils.encode("123456");

    @Override
    public User loadUserByAccount(String account) {
        if (!"admin".equals(account)) {
            return null;
        }
        User user = new User();
        user.setId(1L);
        user.setAccount(account);
        user.setPassword(DEMO_ENCODED_PASSWORD);
        user.setNickname("管理员");
        return user;
    }

    @Override
    public User loadUserById(Long id) {
        if (!Long.valueOf(1L).equals(id)) {
            return null;
        }
        User user = new User();
        user.setId(id);
        user.setAccount("admin");
        user.setPassword(DEMO_ENCODED_PASSWORD);
        user.setNickname("管理员");
        return user;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return PasswordUtils.matches(rawPassword, encodedPassword);
    }
}
