package com.frame.me.base.client;

import com.frame.me.api.annotation.QueryMap;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.util.MultiValueMap;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QueryObjectArgumentResolver} 单元测试.
 *
 * @author frame-me
 */
class QueryObjectArgumentResolverTest {

    private final QueryObjectArgumentResolver resolver = new QueryObjectArgumentResolver();

    /**
     * 数组属性与 Collection 同语义逐项展开为重复 query 参数，null 项跳过.
     */
    @Test
    void arrayPropertiesExpandToRepeatedParams() throws Exception {
        Query query = new Query();
        query.setName("n1");
        query.setTags(new String[]{"a", null, "b"});
        query.setIds(new int[]{1, 2});
        query.setColors(List.of("red", "blue"));

        MultiValueMap<String, String> params = resolve(query);

        assertThat(params.get("tags")).containsExactly("a", "b");
        assertThat(params.get("ids")).containsExactly("1", "2");
        assertThat(params.get("colors")).containsExactly("red", "blue");
        assertThat(params.get("name")).containsExactly("n1");
    }

    /**
     * 未标注 {@link QueryMap} 的参数不处理.
     */
    @Test
    void returnsFalseWithoutQueryMapAnnotation() throws Exception {
        MethodParameter parameter = new MethodParameter(
                SearchClient.class.getMethod("noAnnotation", Query.class), 0);

        assertThat(resolver.resolve(new Query(), parameter, HttpRequestValues.builder())).isFalse();
    }

    private MultiValueMap<String, String> resolve(Query query) throws Exception {
        MethodParameter parameter = new MethodParameter(
                SearchClient.class.getMethod("search", Query.class), 0);
        HttpRequestValues.Builder builder = HttpRequestValues.builder();
        assertThat(resolver.resolve(query, parameter, builder)).isTrue();
        AtomicReference<MultiValueMap<String, String>> paramsRef = new AtomicReference<>();
        builder.configureRequestParams(paramsRef::set);
        return paramsRef.get();
    }

    @SuppressWarnings("unused")
    private interface SearchClient {

        void search(@QueryMap Query query);

        void noAnnotation(Query query);
    }

    /**
     * 测试用查询对象.
     */
    @Data
    static class Query {

        private String name;

        private String[] tags;

        private int[] ids;

        private List<String> colors;
    }
}
