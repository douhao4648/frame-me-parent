package com.frame.me.auth.satoken.config;

import cn.dev33.satoken.dao.SaTokenDao;
import com.frame.me.auth.spi.IAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * multi-redis 缺席时的装配测试.
 *
 * <p>multi-redis 是本模块的 optional 依赖，消费方未引入时
 * {@link SaTokenRedisDaoAutoConfiguration} 必须在 ASM 元数据评估阶段整体退避
 * （不加载 {@code RedisClientRegistry}、不抛 NCDFE），sa-token 运行时回退内存 DAO。
 * 本模块 test classpath 恒有 multi-redis，故用 {@link FilteredClassLoader}
 * 屏蔽 {@code com.frame.me.redis} 包模拟缺席。</p>
 *
 * @author frame-me
 */
class SaTokenRedisDaoAbsentClasspathTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SaTokenAuthAutoConfiguration.class, SaTokenRedisDaoAutoConfiguration.class))
            .withUserConfiguration(SaTokenAuthAutoConfigurationTest.StubUserDetailsConfig.class)
            .withClassLoader(new FilteredClassLoader("com.frame.me.redis"));

    /**
     * RedisClientRegistry 缺席：Redis DAO 配置退避，核心认证 bean 不受影响，零 {@link SaTokenDao} bean.
     */
    @Test
    void redisClientRegistryAbsent_redisDaoBacksOff_coreBeansActive() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(SaTokenDao.class);
            assertThat(context).hasSingleBean(IAuthService.class);
        });
    }
}
