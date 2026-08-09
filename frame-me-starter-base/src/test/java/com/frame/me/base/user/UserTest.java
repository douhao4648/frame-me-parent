package com.frame.me.base.user;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link User} 单元测试.
 *
 * @author frame-me
 */
class UserTest {

    /**
     * password 不参与 toString：口令哈希不得随日志打印落盘.
     */
    @Test
    void toStringExcludesPassword() {
        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        user.setPassword("$2a$10$some-bcrypt-hash");

        String text = user.toString();

        assertThat(text).doesNotContain("$2a$10$some-bcrypt-hash");
        assertThat(text).doesNotContain("password");
        assertThat(text).contains("admin");
    }

    /**
     * Jackson 序列化必须剥离 password（WRITE_ONLY）：/user 等接口响应、
     * sa-token 会话缓存 JSON 都走这条路，口令哈希不出服务端.
     */
    @Test
    void serializeStripsPassword() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        user.setPassword("$2a$10$some-bcrypt-hash");

        String json = new ObjectMapper().writeValueAsString(user);

        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("$2a$10$some-bcrypt-hash");
        assertThat(json).contains("admin");
    }

    /**
     * WRITE_ONLY 不挡反序列化：用户创建/改密请求体仍需接收密码.
     */
    @Test
    void deserializeAcceptsPassword() throws Exception {
        User user = new ObjectMapper().readValue("{\"account\":\"admin\",\"password\":\"plain\"}", User.class);

        assertThat(user.getPassword()).isEqualTo("plain");
    }
}
