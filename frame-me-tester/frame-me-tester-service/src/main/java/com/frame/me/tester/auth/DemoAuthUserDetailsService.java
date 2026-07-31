package com.frame.me.tester.auth;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.util.PasswordUtils;
import com.frame.me.base.user.User;
import org.springframework.stereotype.Service;

/**
 * 示例用户详情服务.
 *
 * <p>仅用于演示，实际项目应从数据库查询用户。
 * 演示密码从环境变量 {@code DEMO_PASSWORD} 读取，默认 {@code 123456} 仅供本地演示，
 * <b>生产环境必须配置强密码</b>，禁止使用默认值.</p>
 *
 * @author frame-me
 */
@Service
public class DemoAuthUserDetailsService implements IAuthUserDetailsService {

    /**
     * 示例加密密码：明文从 {@code DEMO_PASSWORD} 环境变量读取，默认 {@code 123456} 仅供演示.
     */
    private static final String DEMO_ENCODED_PASSWORD = PasswordUtils.encode(
            System.getProperty("DEMO_PASSWORD", System.getenv().getOrDefault("DEMO_PASSWORD", "123456")));

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
}
