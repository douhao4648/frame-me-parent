# 关键文件索引

## 项目配置

| 文件 | 路径 |
|---|---|
| 父 POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/pom.xml` |
| `frame-me-api` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/pom.xml` |
| `frame-me-starter-base` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/pom.xml` |
| `frame-me-adapter` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/pom.xml` |
| `frame-me-adapter-api` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-api/pom.xml` |
| `frame-me-adapter-starter` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/pom.xml` |
| `frame-me-starter-auth` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-auth/pom.xml` |
| `frame-me-starter-dynamic-ds` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-dynamic-ds/pom.xml` |
| `frame-me-starter-multi-redis` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-multi-redis/pom.xml` |
| `frame-me-starter-l1l2-cache` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-l1l2-cache/pom.xml` |
| `frame-me-starter-doc-openapi` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-doc-openapi/pom.xml` |
| `frame-me-tester` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/pom.xml` |
| `frame-me-tester-api` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-api/pom.xml` |
| `frame-me-tester-service` POM | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/pom.xml` |
| 应用配置 | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/main/resources/application.yml` |

## 核心 Java 类

| 类 | 路径 |
|---|---|
| `IResult<T>` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/api/result/IResult.java` |
| `Result<T>` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/result/Result.java` |
| `ResultCode` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/result/ResultCode.java` |
| `BusinessException` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/exception/BusinessException.java` |
| `InternalException` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/exception/InternalException.java` |
| `RetryException` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/exception/RetryException.java` |
| `ApiConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/api/ApiConstant.java` |
| `PageData<T>` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/api/result/PageData.java` |
| `PageQuery` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/api/query/PageQuery.java` |
| `CreateGroup` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/validation/CreateGroup.java` |
| `UpdateGroup` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/validation/UpdateGroup.java` |
| `@TimeRange` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/validation/annotation/TimeRange.java` |
| `TimeRangeValidator` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-api/src/main/java/com/frame/me/validation/validator/TimeRangeValidator.java` |
| `GlobalExceptionHandler` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/advice/GlobalExceptionHandler.java` |
| `BaseAutoConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/config/BaseAutoConfiguration.java` |
| `EnvironmentHelper` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/env/EnvironmentHelper.java` |
| `BaseConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/BaseConstant.java` |
| `BaseEntity` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/entity/BaseEntity.java` |
| `BaseVersionEntity` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/entity/BaseVersionEntity.java` |
| `BaseMetaObjectHandler` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/plugin/BaseMetaObjectHandler.java` |
| `PageUtils`（新规范） | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/util/PageUtils.java` |
| `SnowflakeUtils` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/util/SnowflakeUtils.java` |
| `MybatisPlusConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/java/com/frame/me/base/mybatis/config/MybatisPlusConfiguration.java` |
| `Result2ResponseAdvice` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/advice/Result2ResponseAdvice.java` |
| `Response<T>` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/result/Response.java` |
| `ResponseJacksonModule` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/result/ResponseJacksonModule.java` |
| `PageableUtils`（老规范） | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/mybatis/util/PageableUtils.java` |
| `PageParam`（老规范） | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-api/src/main/java/com/frame/me/adapter/api/query/PageParam.java` |
| `PageResult<T>`（老规范） | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-api/src/main/java/com/frame/me/adapter/api/result/PageResult.java` |
| `AdapterAutoConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/config/AdapterAutoConfiguration.java` |
| `AdapterConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/java/com/frame/me/adapter/AdapterConstant.java` |
| `DynamicDataSourceAutoConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-dynamic-ds/src/main/java/com/frame/me/dynamic/ds/config/DynamicDataSourceAutoConfiguration.java` |
| `MeDynamicDataSourceProvider` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-dynamic-ds/src/main/java/com/frame/me/dynamic/ds/provider/MeDynamicDataSourceProvider.java` |
| `DocOpenApiAutoConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-doc-openapi/src/main/java/com/frame/me/doc/openapi/config/DocOpenApiAutoConfiguration.java` |
| `DocOpenApiProperties` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-doc-openapi/src/main/java/com/frame/me/doc/openapi/config/DocOpenApiProperties.java` |
| `GroupedOpenApiRegistrar` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-doc-openapi/src/main/java/com/frame/me/doc/openapi/config/GroupedOpenApiRegistrar.java` |
| `DocOpenApiConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-doc-openapi/src/main/java/com/frame/me/doc/openapi/DocOpenApiConstant.java` |
| `AuthConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-auth/src/main/java/com/frame/me/auth/AuthConstant.java` |
| `CloudConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-cloud/src/main/java/com/frame/me/cloud/CloudConstant.java` |
| `RedisAutoConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-multi-redis/src/main/java/com/frame/me/redis/config/RedisAutoConfiguration.java` |
| `RedisProperties` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-multi-redis/src/main/java/com/frame/me/redis/config/RedisProperties.java` |
| `RedisUtils` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-multi-redis/src/main/java/com/frame/me/redis/util/RedisUtils.java` |
| `RedisConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-multi-redis/src/main/java/com/frame/me/redis/RedisConstant.java` |
| `CacheAutoConfiguration` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-l1l2-cache/src/main/java/com/frame/me/cache/config/CacheAutoConfiguration.java` |
| `CacheProperties` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-l1l2-cache/src/main/java/com/frame/me/cache/config/CacheProperties.java` |
| `JetCacheInfrastructureRoleFixer` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-l1l2-cache/src/main/java/com/frame/me/cache/config/JetCacheInfrastructureRoleFixer.java` |
| `CacheConstant` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-l1l2-cache/src/main/java/com/frame/me/cache/CacheConstant.java` |
| `Application` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/main/java/com/frame/me/tester/Application.java` |
| `HealthController` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/main/java/com/frame/me/tester/controller/HealthController.java` |
| `DemoController` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/main/java/com/frame/me/tester/controller/DemoController.java` |
| `DemoEntity` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/main/java/com/frame/me/tester/entity/DemoEntity.java` |
| `DemoMapper` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/main/java/com/frame/me/tester/mapper/DemoMapper.java` |
| `ApplicationTests` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/ApplicationTests.java` |
| `AbstractIntegrationTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/AbstractIntegrationTest.java` |
| `DemoMapperIntegrationTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/DemoMapperIntegrationTest.java` |
| `MybatisPlusCrudAndFillTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusCrudAndFillTest.java` |
| `MybatisPlusLogicDeleteTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusLogicDeleteTest.java` |
| `MybatisPlusOptimisticLockTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusOptimisticLockTest.java` |
| `MybatisPlusPaginationTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/mybatis/MybatisPlusPaginationTest.java` |
| `DemoServiceCacheTest` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-tester/frame-me-tester-service/src/test/java/com/frame/me/tester/cache/DemoServiceCacheTest.java` |

## Spring Boot 自动装配注册文件

| 模块 | 文件路径 |
|---|---|
| `frame-me-starter-base` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-base/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `frame-me-adapter-starter` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-adapter/frame-me-adapter-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `frame-me-starter-dynamic-ds` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-dynamic-ds/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `frame-me-starter-multi-redis` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-multi-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `frame-me-starter-l1l2-cache` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-l1l2-cache/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `frame-me-starter-doc-openapi` | `/Users/douhao4648/Documents/Frame_Me/frame-me-parent/frame-me-starter-doc-openapi/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

## 速查表：类名 → 作用

| 类名 | 作用 |
|---|---|
| `IResult<T>` | 统一响应结果接口 |
| `Result<T>` | `IResult<T>` 默认实现，并提供静态工厂方法 |
| `PageData<T>` | 新规范分页结果 |
| `PageQuery` | 新规范分页查询参数 |
| `ResultCode` | 状态码枚举 |
| `BusinessException` | 可预期业务异常 |
| `InternalException` | 系统内部异常，HTTP 500 |
| `RetryException` | 可重试异常（待完善处理） |
| `CreateGroup` / `UpdateGroup` | 校验分组：新增 / 更新场景 |
| `@TimeRange` | 类级时间范围校验注解 |
| `GlobalExceptionHandler` | 全局异常处理 |
| `EnvironmentHelper` | 获取 Spring active profile、判断 dev/test/prod/daily/pre |
| `BaseEntity` | MyBatis-Plus 基础实体，含公共字段，主键雪花算法 |
| `BaseVersionEntity` | 继承 BaseEntity，额外提供 version（乐观锁） |
| `BaseMetaObjectHandler` | 公共字段自动填充处理器，需通过配置开启 |
| `PageUtils` | 新规范分页工具，`PageQuery` / `PageData` 与 MyBatis-Plus `Page` 转换 |
| `SnowflakeUtils` | 基于 Spring 容器获取 `IdentifierGenerator` 生成雪花 ID |
| `MybatisPlusProperties` | `me.mybatis` 配置属性绑定 |
| `MybatisPlusConfiguration` | MyBatis-Plus 自动装配入口，注册分页插件、乐观锁插件、公共字段自动填充处理器以及可选的自定义 ID 生成器 |
| `Result2ResponseAdvice` | 将 `IResult<T>` 转换为 `Response<T>` |
| `Response<T>` | 外部 JSON 响应结构 |
| `ResponseJacksonModule` | 将 `IResult` 抽象类型反序列化映射为 `Response` |
| `PageParam` | 老规范分页查询参数（`frame-me-adapter-api`） |
| `PageResult<T>` | 老规范分页结果（`frame-me-adapter-api`） |
| `PageableUtils` | 老规范分页工具，`PageParam` / `PageResult` 与 MyBatis-Plus `Page` 转换 |
| `DynamicDataSourceAutoConfiguration` | 多数据源自动装配入口 |
| `MeDynamicDataSourceProvider` | 根据 `spring.datasource.*` 创建默认 `master` 数据源 |
| `RedisAutoConfiguration` | Redis 基础能力自动装配入口 |
| `RedisProperties` | `me.redis` 配置属性绑定 |
| `RedisUtils` | 统一 Redis 操作工具类 |
| `CacheAutoConfiguration` | JetCache 两级缓存自动装配入口 |
| `CacheProperties` | `me.cache` 配置属性绑定 |
| `JetCacheInfrastructureRoleFixer` | 修复 JetCache 内部配置类的 BeanPostProcessor 警告 |
| `DocOpenApiAutoConfiguration` | OpenAPI 文档自动装配入口 |
| `DocOpenApiProperties` | `me.swagger` 配置属性绑定 |
| `GroupedOpenApiRegistrar` | 动态注册 API 分组 |

### EnvironmentHelper 方法

| 方法 | 说明 |
|---|---|
| `String[] getActiveProfiles()` | 返回当前所有 active profile，未配置时返回空数组 |
| `String getActiveProfile()` | 返回首个 active profile，未配置时返回 `"default"` |
| `boolean isProfileActive(String profile)` | 判断指定 profile 是否激活 |
| `boolean isDev()` | 是否激活 `dev` profile |
| `boolean isTest()` | 是否激活 `test` profile |
| `boolean isProd()` | 是否激活 `prod` profile |
| `boolean isDaily()` | 是否激活 `daily` profile |
| `boolean isPre()` | 是否激活 `pre` profile |

## 已知扩展点

1. **`IResult.rid` / `Response.requestId` 未填充**
   - 字段已预留，但没有任何工厂方法或 Advice 为其赋值。
   - 适合扩展：TraceId 生成过滤器、MDC 透传等。

2. **`RetryException` 未单独处理**
   - `GlobalExceptionHandler` 没有 `@ExceptionHandler(RetryException.class)`。
   - 当前会落入通用 `Exception` 处理器，返回 HTTP 默认 200 + code 500。

3. **`frame-me-starter-auth` 与 `frame-me-starter-cloud` 为空壳**
   - 当前仅包含占位常量接口。
   - 适合作为未来认证、鉴权、注册中心、配置中心、网关等能力的载体。

4. **`HealthController` 故意触发 NPE**
   - 实现 `IHealthApi`（`@HttpExchange("/api/health")`），用于验证异常处理链路是否正常工作。
   - 若后续需要真正的健康检查接口，需重写该方法。

5. **`frame-me-tester` 已拆分为 `frame-me-tester-api` + `frame-me-tester-service`**
   - API 契约放在 `frame-me-tester-api`，使用 Spring HTTP Interface 声明。
   - 实现与可运行入口放在 `frame-me-tester-service`。

6. **Spring Cloud / Spring Cloud Alibaba BOM 已声明但未使用**
   - 父 POM 中已导入 `spring-cloud-dependencies` 与 `spring-cloud-alibaba-dependencies`。
   - 尚未在任何模块中引入具体 starter，为后续微服务化预留。
