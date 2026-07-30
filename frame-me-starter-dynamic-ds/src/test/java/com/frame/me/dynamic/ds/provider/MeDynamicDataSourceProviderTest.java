package com.frame.me.dynamic.ds.provider;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MeDynamicDataSourceProvider} 配置优先级测试.
 *
 * @author frame-me
 */
class MeDynamicDataSourceProviderTest {

    /**
     * 高优先级源的 hikari 配置不应被低优先级源覆盖：
     * getPropertySources 迭代为高→低，putIfAbsent 让高优先级先占位.
     *
     * <p>旧实现用 put 覆盖，低优先级源（配置文件）会覆盖高优先级源（命令行/环境变量），
     * 与 environment.getProperty 的优先级解析不一致.</p>
     */
    @Test
    void hikariPropertyHigherPrioritySourceWins() {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        // 先添加低优先级（MapPropertySource 默认 addLast 后再调整顺序）
        sources.addLast(new MapPropertySource("low",
                Map.of("spring.datasource.url", "jdbc:low",
                        "spring.datasource.hikari.maximum-pool-size", "10")));
        // 高优先级覆盖到前面
        sources.addFirst(new MapPropertySource("high",
                Map.of("spring.datasource.hikari.maximum-pool-size", "50")));

        @SuppressWarnings("unchecked")
        DefaultDataSourceCreator creator = mock(DefaultDataSourceCreator.class);
        DataSource ds = mock(DataSource.class);
        ArgumentCaptor<DataSourceProperty> captor = ArgumentCaptor.forClass(DataSourceProperty.class);
        when(creator.createDataSource(captor.capture())).thenReturn(ds);

        MeDynamicDataSourceProvider provider = new MeDynamicDataSourceProvider(creator, env);
        provider.loadDataSources();

        DataSourceProperty property = captor.getValue();
        // 高优先级源的值胜出（50），不被低优先级源（10）覆盖.
        // 注：baomidou HikariCpConfig.setMaximumPoolSize 写 maxPoolSize 字段，
        // 故用 getMaxPoolSize 读取对应值.
        assertThat(property.getHikari()).as("hikari config should be populated").isNotNull();
        assertThat(property.getHikari().getMaxPoolSize()).isEqualTo(50);
    }

    /**
     * 未配 url 时返回空 Map，不创建数据源.
     */
    @Test
    void emptyWhenNoUrlConfigured() {
        StandardEnvironment env = new StandardEnvironment();
        @SuppressWarnings("unchecked")
        DefaultDataSourceCreator creator = mock(DefaultDataSourceCreator.class);

        MeDynamicDataSourceProvider provider = new MeDynamicDataSourceProvider(creator, env);
        Map<String, DataSource> result = provider.loadDataSources();

        assertThat(result).isEmpty();
    }
}
