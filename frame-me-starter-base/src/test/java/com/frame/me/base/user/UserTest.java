package com.frame.me.base.user;

import org.junit.jupiter.api.Test;

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
}
