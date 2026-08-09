package com.frame.me.sso;

import com.frame.me.sso.event.UserLogoutEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSO 上下文加载测试.
 *
 * @author frame-me
 */
@ActiveProfiles("test")
@SpringBootTest
class ContextLoadTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能加载
    }

    /**
     * sso-api 的 {@code UserLogoutEventConfiguration} 未显式 {@code @Import}，
     * 其包 {@code com.frame.me.sso.event} 在扫描根包 {@code com.frame.me.sso} 之下，
     * 应由组件扫描自动注册事件类型（SSO 多实例踢人广播互通的前提）.
     */
    @Test
    void userLogoutEventTypeRegisteredByComponentScan() {
        assertThat(context.getBean(UserLogoutEventType.class)).isNotNull();
    }
}
