# frame-me-sso 单点登录服务 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `frame-me-launcher/frame-me-sso` 从占位空壳重构为聚合工程（api 契约 + service 启动服务），填充为独立的 Spring Boot SSO 认证服务，支持集群内应用免密钥接入、集群外三方应用授权码换 token、RS256 JWT 自验签、三层踢人机制。

**Architecture:** SSO 是聚合工程（参考 tester）：`frame-me-sso`（聚合 pom）+ `frame-me-sso-api`（契约：`@HttpExchange` 接口 + dto/vo）+ `frame-me-sso-service`（启动服务）。service 模块包结构：`controller/service/entity/mapper` 顶层保留，其余（Constant/config/jwt/enums/event/web）统一归 `infrastructure` 包。sa-token 仅用于 SSO 自身会话治理，不参与下游 token 签发——下游 token 由 SSO 用 RS256 私钥自签，下游持公钥自验签。

**Tech Stack:** Spring Boot 4.0.7 + Java 25 + sa-token 1.45.0 + MyBatis-Flex + multi-redis + sensi-encrypt + jjwt(RS256) + op-audit + msg-notify + doc-openapi

## Global Constraints

- **JDK 25**，`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home`
- **父 pom BOM 管控版本**，子模块 pom 不写 version
- **工程结构**：聚合 pom（`frame-me-sso` packaging=pom）+ `frame-me-sso-api`（契约，引 `frame-me-api`）+ `frame-me-sso-service`（启动服务，不引 boot）
- **service 包结构**：`controller/service/entity/mapper` 顶层保留；其余（Constant/config/jwt/enums/event/web）归 `infrastructure/*`
- **实体继承** `com.frame.me.mybatis.flex.entity.BaseEntity`（内置雪花 id / createTime / updateTime / deleted）
- **表前缀** `fm_sso_*`（默认），实体名剥离前缀（`SsoApp` / `SsoAuthCode` / `SsoUser`）
- **接口名加 `I` 前缀**（如 `IJwtSigner`、`ISsoAuthApi`）
- **api 契约用 `@HttpExchange`**，放 `com.frame.me.sso.api`，下游引 api 模块用 Spring HTTP Interface 调用
- **`*Constant` 为 final class + 私有构造器**，放 `infrastructure` 包
- **中文 Javadoc**，类不声明 final（Constant 例外）
- **不引** boot/cloud/cloud-nacos/auth-rbac/dynamic-ds/l1l2-cache/sse-mvc/ws-mvc
- **密码** BCrypt，`me.auth.bcrypt-strength` 默认 12（复用 `PasswordUtils`）
- **端口分离**：业务 9090 / management 9091
- **不自动提交**，任何 commit 前征求用户同意

---

## File Structure

### 工程结构

```
frame-me-launcher/frame-me-sso/               [改为聚合 pom，packaging=pom]
├── pom.xml                                    [聚合，声明两个子模块]
├── frame-me-sso-api/                          [契约模块]
│   ├── pom.xml                                [引 frame-me-api + spring-web]
│   └── src/main/java/com/frame/me/sso/api/
│       ├── ISsoAuthApi.java                   [@HttpExchange 授权码流程契约]
│       ├── ISsoAdminApi.java                  [@HttpExchange 管理端点契约]
│       ├── dto/
│       │   ├── TokenRequestDTO.java           [换 token 请求]
│       │   ├── AuthorizeRequestDTO.java       [授权请求参数]
│       │   └── RegisterAppDTO.java            [注册应用请求]
│       └── vo/
│           ├── TokenVO.java                   [复用 auth 的 TokenVO 或新建]
│           ├── JwksVO.java
│           └── AppVO.java
└── frame-me-sso-service/                      [启动服务模块]
    ├── pom.xml                                [引各 starter + spring-boot-maven-plugin]
    └── src/
        ├── main/java/com/frame/me/sso/
        │   ├── SsoApplication.java
        │   ├── controller/
        │   │   ├── SsoAuthController.java     [实现 ISsoAuthApi]
        │   │   └── SsoAdminController.java    [实现 ISsoAdminApi]
        │   ├── service/
        │   │   ├── SsoAppService.java
        │   │   ├── SsoAuthCodeService.java
        │   │   ├── SsoUserService.java
        │   │   ├── SsoTokenService.java
        │   │   ├── SsoUserDetailsService.java  [implements IAuthUserDetailsService]
        │   │   └── SsoLogoutService.java
        │   ├── entity/
        │   │   ├── SsoApp.java
        │   │   ├── SsoAuthCode.java
        │   │   └── SsoUser.java
        │   ├── mapper/
        │   │   ├── SsoAppMapper.java
        │   │   ├── SsoAuthCodeMapper.java
        │   │   └── SsoUserMapper.java
        │   └── infrastructure/               ← 其余全部归此
        │       ├── SsoConstant.java
        │       ├── config/
        │       │   ├── SsoProperties.java
        │       │   └── SsoJwtConfiguration.java
        │       ├── jwt/
        │       │   ├── IJwtSigner.java
        │       │   └── Rs256JwtSigner.java
        │       ├── enums/
        │       │   ├── AccessType.java
        │       │   ├── AppStatus.java
        │       │   └── TokenEndpointAuthMethod.java
        │       ├── event/
        │       │   └── UserLogoutEvent.java
        │       └── web/
        │           └── SsoStpInterface.java
        ├── main/resources/
        │   ├── application.yml
        │   ├── schema.sql
        │   ├── sso-private.pem
        │   ├── sso-public.pem
        │   └── static/sso-login.html
        └── test/java/com/frame/me/sso/
            ├── SsoContextLoadTest.java
            ├── SsoTokenServiceTest.java
            ├── SsoAuthCodeServiceTest.java
            ├── SsoAppServiceTest.java
            └── SsoAuthControllerTest.java
```

**修改的现有文件**：
- `docs/modules.md` / `docs/reference.md` / `docs/architecture.md` / `docs/conventions.md`
- `docs/guides/sso.md`（新建）
- `CLAUDE.md`

**注**：原 `frame-me-launcher/frame-me-sso` 是单 pom + 单 `SsoConstant`。重构为聚合工程时，需在 `frame-me-launcher/pom.xml` 确认 `<module>frame-me-sso</module>` 仍指向新聚合模块（路径不变，launcher 仍聚合它）。原 `SsoConstant.java` 迁移到 service 模块的 `infrastructure` 包。

---

## Task 1: 聚合工程骨架（pom + api + service 模块结构）

**Files:**
- Modify: `frame-me-launcher/frame-me-sso/pom.xml`（改为聚合 pom）
- Create: `frame-me-launcher/frame-me-sso/frame-me-sso-api/pom.xml`
- Create: `frame-me-launcher/frame-me-sso/frame-me-sso-service/pom.xml`
- Create: `frame-me-launcher/frame-me-sso/frame-me-sso-service/src/main/java/com/frame/me/sso/SsoApplication.java`
- Create: `frame-me-launcher/frame-me-sso/frame-me-sso-service/src/main/resources/application.yml`
- Move: 原 `SsoConstant.java` → `infrastructure/SsoConstant.java`

**Interfaces:**
- Produces: 聚合工程结构、`SsoApplication`、`SsoConstant`

- [ ] **Step 1: 重写聚合 pom**

`frame-me-launcher/frame-me-sso/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-launcher</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>frame-me-sso</artifactId>
    <packaging>pom</packaging>
    <name>frame-me-sso</name>
    <description>SSO 单点登录服务聚合工程（api 契约 + service 启动服务）</description>

    <modules>
        <module>frame-me-sso-api</module>
        <module>frame-me-sso-service</module>
    </modules>
</project>
```

- [ ] **Step 2: 新建 api 模块 pom**

`frame-me-launcher/frame-me-sso/frame-me-sso-api/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-sso</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>frame-me-sso-api</artifactId>
    <name>frame-me-sso-api</name>
    <description>SSO API 契约模块（@HttpExchange 接口 + dto/vo）</description>

    <dependencies>
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 新建 service 模块 pom**

`frame-me-launcher/frame-me-sso/frame-me-sso-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.frame.me</groupId>
        <artifactId>frame-me-sso</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>frame-me-sso-service</artifactId>
    <name>frame-me-sso-service</name>
    <description>SSO 单点登录服务：集群内免密钥接入、集群外授权码换 token、RS256 JWT 自验签</description>

    <dependencies>
        <!-- 本工程契约 -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-sso-api</artifactId>
        </dependency>
        <!-- 认证：sa-token 会话治理（传递引入 auth SPI + base web 底座） -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-auth-sa-token</artifactId>
        </dependency>
        <!-- 持久层 -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-mybatis-flex</artifactId>
        </dependency>
        <!-- SSO 自用 Redis -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-multi-redis</artifactId>
        </dependency>
        <!-- 敏感配置加密 -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-sensi-encrypt</artifactId>
        </dependency>
        <!-- 审计 -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-op-audit</artifactId>
        </dependency>
        <!-- 通知 -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-msg-notify</artifactId>
        </dependency>
        <!-- 文档 -->
        <dependency>
            <groupId>com.frame.me</groupId>
            <artifactId>frame-me-starter-doc-openapi</artifactId>
        </dependency>
        <!-- JWT 签发（jjwt） -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-gson</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths combine.children="append">
                        <path>
                            <groupId>com.mybatis-flex</groupId>
                            <artifactId>mybatis-flex-processor</artifactId>
                            <version>${mybatis-flex.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: 删除原占位 SsoConstant（迁移到 infrastructure 包）**

```bash
# 删除原位置
rm frame-me-launcher/frame-me-sso/src/main/java/com/frame/me/sso/SsoConstant.java
# 清空原 src 目录（聚合模块不再有源码）
rm -rf frame-me-launcher/frame-me-sso/src
```

- [ ] **Step 5: 新建 SsoApplication**

`frame-me-launcher/frame-me-sso/frame-me-sso-service/src/main/java/com/frame/me/sso/SsoApplication.java`:

```java
package com.frame.me.sso;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * SSO 单点登录服务启动类.
 *
 * @author frame-me
 */
@SpringBootApplication
@MapperScan("com.frame.me.sso.mapper")
@EnableConfigurationProperties(com.frame.me.sso.infrastructure.config.SsoProperties.class)
public class SsoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoApplication.class, args);
    }
}
```

- [ ] **Step 6: 新建 SsoConstant（infrastructure 包）**

`frame-me-launcher/frame-me-sso/frame-me-sso-service/src/main/java/com/frame/me/sso/infrastructure/SsoConstant.java`:

```java
package com.frame.me.sso.infrastructure;

/**
 * SSO 服务常量.
 *
 * @author frame-me
 */
public final class SsoConstant {

    private SsoConstant() {
    }

    /** SSO 签发方标识. */
    public static final String ISSUER = "frame-me-sso";

    /** Account-Session 中缓存用户快照的 key. */
    public static final String SESSION_USER_KEY = "user";

    /** 授权码 Redis key 前缀. */
    public static final String AUTH_CODE_KEY_PREFIX = "sso:auth-code:";

    /** token 黑名单 Redis key 前缀. */
    public static final String TOKEN_BLACKLIST_KEY_PREFIX = "sso:blacklist:";
}
```

- [ ] **Step 7: 新建 application.yml**

`frame-me-launcher/frame-me-sso/frame-me-sso-service/src/main/resources/application.yml`:

```yaml
server:
  port: 9090

management:
  server:
    port: 9091

spring:
  application:
    name: frame-me-sso
  jackson:
    default-property-inclusion: non_null
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  data:
    redis:
      database: 0
      host: localhost
      port: 6379

mybatis-flex:
  mapper-locations: classpath*:/mapper/**/*.xml
  global-config:
    logic-delete-column: deleted
    normal-value-of-logic-delete: 0
    deleted-value-of-logic-delete: 1
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  datasource:
    master:
      url: jdbc:mysql://localhost:3306/frame_me_sso?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      username: root
      password: root
      driver-class-name: com.mysql.cj.jdbc.Driver
      type: hikari
      maximum-pool-size: 10
      minimum-idle: 5

me:
  sso:
    enabled: true
    path: /api/sso
    table-prefix: fm_
    login-page:
      enabled: true
    auth-code:
      expires: 60s
    token:
      access-expires: PT2H
      refresh-expires: P7D
    jwt:
      private-key: classpath:sso-private.pem
      public-key: classpath:sso-public.pem
      issuer: frame-me-sso
  auth:
    enabled: true
    enforce-login: true
    whitelist:
      - /api/sso/authorize
      - /api/sso/login-page
      - /api/sso/login
      - /api/sso/token
      - /api/sso/refresh
      - /api/sso/jwks
      - /actuator/**
    sa-token:
      enabled: true
      path: /api/auth
      jwt:
        enabled: false
      redis:
        enabled: true
        client-name: default

sa-token:
  token-name: satoken
  timeout: 7200
  active-timeout: -1
  is-concurrent: true
  is-share: true
  is-read-cookie: true
  is-read-header: true
```

- [ ] **Step 8: 编译验证**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home
./mvnw -pl frame-me-launcher/frame-me-sso -am clean compile -q
```

Expected: BUILD SUCCESS（此时 api/service 模块暂无业务源码，仅骨架）

- [ ] **Step 9: Commit（征求用户同意后）**

```bash
git add frame-me-launcher/frame-me-sso/
git commit -m "refactor(sso): 重构为聚合工程（api 契约 + service 启动服务）"
```

---

## Task 2: api 契约模块（接口 + dto/vo）

**Files:**
- Create: `.../sso/api/ISsoAuthApi.java`
- Create: `.../sso/api/ISsoAdminApi.java`
- Create: `.../sso/api/dto/TokenRequestDTO.java`
- Create: `.../sso/api/dto/RegisterAppDTO.java`
- Create: `.../sso/api/vo/TokenVO.java`
- Create: `.../sso/api/vo/JwksVO.java`
- Create: `.../sso/api/vo/AppVO.java`

**Interfaces:**
- Produces: api 契约，供 service 实现、下游引 api 模块用 Spring HTTP Interface 调用

- [ ] **Step 1: TokenVO（api 模块）**

`frame-me-sso-api/src/main/java/com/frame/me/sso/api/vo/TokenVO.java`:

```java
package com.frame.me.sso.api.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * Token 响应 VO.
 *
 * @author frame-me
 */
@Data
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Access Token（RS256 JWT）. */
    private String accessToken;

    /** Refresh Token（首期 null，留作演进）. */
    private String refreshToken;
}
```

- [ ] **Step 2: TokenRequestDTO**

```java
package com.frame.me.sso.api.dto;

import lombok.Data;

/**
 * 换 token 请求 DTO.
 *
 * @author frame-me
 */
@Data
public class TokenRequestDTO {

    /** 授权码. */
    private String code;

    /** 应用 ID. */
    private String appId;

    /** 应用密钥（INTERNAL 免）. */
    private String appSecret;

    /** 回调地址. */
    private String redirectUri;

    /** 防重放（可选）. */
    private String nonce;
}
```

- [ ] **Step 3: RegisterAppDTO**

```java
package com.frame.me.sso.api.dto;

import lombok.Data;
import java.util.List;

/**
 * 注册应用请求 DTO.
 *
 * @author frame-me
 */
@Data
public class RegisterAppDTO {

    private String appName;
    /** INTERNAL / EXTERNAL */
    private String accessType;
    private List<String> redirectUris;
    private String scopes;
    /** none / client_secret_post / client_secret_basic */
    private String tokenEndpointAuthMethod;
}
```

- [ ] **Step 4: JwksVO / AppVO**

```java
// vo/JwksVO.java
package com.frame.me.sso.api.vo;
import lombok.Data;
import java.util.Map;

@Data
public class JwksVO {
    private String kty;
    private String alg;
    private String use;
    private String location;
}

// vo/AppVO.java
package com.frame.me.sso.api.vo;
import lombok.Data;

@Data
public class AppVO {
    private String appId;
    private String appName;
    private String accessType;
    /** 仅注册/重置时返回明文，否则 null */
    private String appSecret;
    private String status;
}
```

- [ ] **Step 5: ISsoAuthApi 契约**

`frame-me-sso-api/src/main/java/com/frame/me/sso/api/ISsoAuthApi.java`:

```java
package com.frame.me.sso.api;

import com.frame.me.api.result.IResult;
import com.frame.me.sso.api.dto.TokenRequestDTO;
import com.frame.me.sso.api.vo.TokenVO;
import com.frame.me.sso.api.vo.JwksVO;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.GetExchange;

/**
 * SSO 授权码流程 API 契约.
 *
 * <p>下游应用引本 api 模块，用 Spring HTTP Interface 调用 SSO 服务的 token/refresh/jwks 端点。</p>
 *
 * @author frame-me
 */
@HttpExchange("/api/sso")
public interface ISsoAuthApi {

    /**
     * 换 token：授权码 → RS256 JWT.
     */
    @PostExchange("/token")
    IResult<TokenVO> token(TokenRequestDTO request);

    /**
     * 刷新 token.
     */
    @PostExchange("/refresh")
    IResult<TokenVO> refresh(String authorizationHeader);

    /**
     * JWKS 公钥信息.
     */
    @GetExchange("/jwks")
    IResult<JwksVO> jwks();
}
```

- [ ] **Step 6: ISsoAdminApi 契约**

```java
package com.frame.me.sso.api;

import com.frame.me.api.result.IResult;
import com.frame.me.sso.api.dto.RegisterAppDTO;
import com.frame.me.sso.api.vo.AppVO;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.DeleteExchange;

import java.util.List;

/**
 * SSO 管理端点 API 契约（需 admin 角色）.
 *
 * @author frame-me
 */
@HttpExchange("/api/sso/admin")
public interface ISsoAdminApi {

    @PostExchange("/app")
    IResult<AppVO> registerApp(RegisterAppDTO dto);

    @PutExchange("/app/{appId}")
    IResult<Boolean> updateApp(String appId, AppVO app);

    @PostExchange("/app/{appId}/reset-secret")
    IResult<AppVO> resetSecret(String appId);

    @DeleteExchange("/app/{appId}")
    IResult<Boolean> disableApp(String appId);

    @GetExchange("/apps")
    IResult<List<AppVO>> listApps();

    @PostExchange("/user/{userId}/logout")
    IResult<Boolean> forceLogout(Long userId, String appId, String reason);
}
```

- [ ] **Step 7: 编译 api 模块**

```bash
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-api -am compile -q
```

- [ ] **Step 8: Commit（征求同意）**

---

## Task 3: infrastructure 枚举 + 配置

**Files:**
- Create: `.../sso/infrastructure/enums/AccessType.java`
- Create: `.../sso/infrastructure/enums/AppStatus.java`
- Create: `.../sso/infrastructure/enums/TokenEndpointAuthMethod.java`
- Create: `.../sso/infrastructure/config/SsoProperties.java`

**Interfaces:**
- Produces: 三个枚举 + `SsoProperties`（`me.sso` 前缀）

- [ ] **Step 1: AccessType / AppStatus / TokenEndpointAuthMethod**

```java
// infrastructure/enums/AccessType.java
package com.frame.me.sso.infrastructure.enums;

/**
 * 应用接入类型.
 *
 * @author frame-me
 */
public enum AccessType {
    /** 集群内可信应用，免密钥接入. */
    INTERNAL,
    /** 集群外三方应用，强制密钥校验. */
    EXTERNAL
}

// infrastructure/enums/AppStatus.java
package com.frame.me.sso.infrastructure.enums;

/**
 * 应用状态.
 *
 * @author frame-me
 */
public enum AppStatus {
    ACTIVE,
    DISABLED
}

// infrastructure/enums/TokenEndpointAuthMethod.java
package com.frame.me.sso.infrastructure.enums;

/**
 * 换 token 端点鉴权方式（对齐 OIDC 标准枚举值）.
 *
 * @author frame-me
 */
public enum TokenEndpointAuthMethod {
    /** 内部应用免密钥. */
    none,
    /** client_secret 放 POST body. */
    client_secret_post,
    /** client_secret 放 Basic Authorization 头. */
    client_secret_basic
}
```

- [ ] **Step 2: SsoProperties**

`infrastructure/config/SsoProperties.java`:

```java
package com.frame.me.sso.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSO 服务配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.sso")
public class SsoProperties {

    private boolean enabled = true;
    private String path = "/api/sso";
    private String tablePrefix = "fm_";
    private LoginPage loginPage = new LoginPage();
    private AuthCode authCode = new AuthCode();
    private Token token = new Token();
    private Jwt jwt = new Jwt();

    @Data
    public static class LoginPage {
        private boolean enabled = true;
    }

    @Data
    public static class AuthCode {
        private Duration expires = Duration.ofSeconds(60);
    }

    @Data
    public static class Token {
        private Duration accessExpires = Duration.ofHours(2);
        private Duration refreshExpires = Duration.ofDays(7);
    }

    @Data
    public static class Jwt {
        private String privateKey;
        private String publicKey;
        private String issuer = "frame-me-sso";
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service -am compile -q
```

- [ ] **Step 4: Commit（征求同意）**

---

## Task 4: 实体 + Mapper + schema.sql

**Files:**
- Create: `.../sso/entity/SsoApp.java`（含 transient appSecretPlain）
- Create: `.../sso/entity/SsoAuthCode.java`
- Create: `.../sso/entity/SsoUser.java`
- Create: `.../sso/mapper/SsoAppMapper.java` + `SsoAuthCodeMapper.java` + `SsoUserMapper.java`
- Create: `.../resources/schema.sql`

**Interfaces:**
- Produces: 三个实体（继承 flex `BaseEntity`）+ Mapper

- [ ] **Step 1: SsoApp 实体**

```java
package com.frame.me.sso.entity;

import com.frame.me.mybatis.flex.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SSO 应用注册表实体.
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("fm_sso_app")
public class SsoApp extends BaseEntity {

    @Column
    private String appId;
    @Column
    private String appName;
    @Column
    private String accessType;
    /** INTERNAL 为 null，EXTERNAL 存加密后密钥. */
    @Column
    private String appSecret;
    @Column
    private String redirectUris;
    @Column
    private String scopes;
    @Column
    private String tokenEndpointAuthMethod;
    @Column
    private String status;

    /** 注册/重置时返回明文用，不入库. */
    @Column(ignore = true)
    private transient String appSecretPlain;
}
```

- [ ] **Step 2: SsoAuthCode 实体**

```java
package com.frame.me.sso.entity;

import com.frame.me.mybatis.flex.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * SSO 授权码实体.
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("fm_sso_auth_code")
public class SsoAuthCode extends BaseEntity {

    @Column
    private String code;
    @Column
    private String appId;
    @Column
    private Long userId;
    @Column
    private String scopes;
    @Column
    private String redirectUri;
    @Column
    private LocalDateTime expiresAt;
    @Column
    private Integer used;
}
```

- [ ] **Step 3: SsoUser 实体**

```java
package com.frame.me.sso.entity;

import com.frame.me.mybatis.flex.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SSO 用户实体.
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("fm_sso_user")
public class SsoUser extends BaseEntity {

    @Column
    private String account;
    @Column
    private String password;
    @Column
    private String name;
    @Column
    private String status;
    @Column
    private String roles;
}
```

- [ ] **Step 4: 三个 Mapper**

```java
// mapper/SsoAppMapper.java
package com.frame.me.sso.mapper;
import com.mybatisflex.core.BaseMapper;
import com.frame.me.sso.entity.SsoApp;
public interface SsoAppMapper extends BaseMapper<SsoApp> {}

// mapper/SsoAuthCodeMapper.java
package com.frame.me.sso.mapper;
import com.mybatisflex.core.BaseMapper;
import com.frame.me.sso.entity.SsoAuthCode;
public interface SsoAuthCodeMapper extends BaseMapper<SsoAuthCode> {}

// mapper/SsoUserMapper.java
package com.frame.me.sso.mapper;
import com.mybatisflex.core.BaseMapper;
import com.frame.me.sso.entity.SsoUser;
public interface SsoUserMapper extends BaseMapper<SsoUser> {}
```

- [ ] **Step 5: schema.sql**

```sql
CREATE TABLE IF NOT EXISTS fm_sso_app (
    id BIGINT NOT NULL PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    access_type VARCHAR(16) NOT NULL,
    app_secret VARCHAR(256),
    redirect_uris TEXT,
    scopes VARCHAR(256),
    token_endpoint_auth_method VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_app_id (app_id)
);

CREATE TABLE IF NOT EXISTS fm_sso_auth_code (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    scopes VARCHAR(256),
    redirect_uri VARCHAR(512),
    expires_at DATETIME,
    used INT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_code (code)
);

CREATE TABLE IF NOT EXISTS fm_sso_user (
    id BIGINT NOT NULL PRIMARY KEY,
    account VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    name VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    roles VARCHAR(256),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_account (account)
);
```

- [ ] **Step 6: 编译验证**

```bash
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service -am compile -q
```

- [ ] **Step 7: Commit（征求同意）**

---

## Task 5: JWT 签发器（IJwtSigner + Rs256JwtSigner）

**Files:**
- Create: `.../sso/infrastructure/jwt/IJwtSigner.java`
- Create: `.../sso/infrastructure/jwt/Rs256JwtSigner.java`
- Create: `.../sso/infrastructure/config/SsoJwtConfiguration.java`
- Create: `.../resources/sso-private.pem` + `sso-public.pem`（openssl 生成）
- Create: `src/test/.../Rs256JwtSignerTest.java`

**Interfaces:**
- Produces: `IJwtSigner`（`sign(claims)` / `verify(token)`）

- [ ] **Step 1: 生成测试用 RSA 密钥对**

```bash
cd frame-me-launcher/frame-me-sso/frame-me-sso-service/src/main/resources
openssl genrsa -out sso-private.pem 2048
openssl rsa -in sso-private.pem -pubout -out sso-public.pem
```

- [ ] **Step 2: 先写测试**

`src/test/java/com/frame/me/sso/Rs256JwtSignerTest.java`:

```java
package com.frame.me.sso;

import com.frame.me.sso.infrastructure.jwt.Rs256JwtSigner;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Rs256JwtSignerTest {

    @Test
    void signAndVerify() {
        Rs256JwtSigner signer = new Rs256JwtSigner(
                "classpath:sso-private.pem", "classpath:sso-public.pem", "frame-me-sso");
        String token = signer.sign(Map.of("sub", "1001", "aud", "fm-test"));
        assertNotNull(token);
        Map<String, Object> claims = signer.verify(token);
        assertEquals("1001", claims.get("sub"));
        assertEquals("fm-test", claims.get("aud"));
        assertEquals("frame-me-sso", claims.get("iss"));
        assertNotNull(claims.get("exp"));
    }

    @Test
    void missingPrivateKeyFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new Rs256JwtSigner(null, "classpath:sso-public.pem", "frame-me-sso"));
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

```bash
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service test -Dtest=Rs256JwtSignerTest -q
```

Expected: FAIL（类不存在）

- [ ] **Step 4: IJwtSigner 接口**

```java
package com.frame.me.sso.infrastructure.jwt;

import java.util.Map;

/**
 * JWT 签名器接口（可插拔，现期 RS256，将来可扩展 ES256/JWKS）.
 *
 * @author frame-me
 */
public interface IJwtSigner {

    /**
     * 签发 JWT.
     *
     * @param claims payload 声明
     * @return JWT 字符串
     */
    String sign(Map<String, Object> claims);

    /**
     * 验签并解析 JWT.
     *
     * @param token JWT 字符串
     * @return claims；验签失败抛异常
     */
    Map<String, Object> verify(String token);
}
```

- [ ] **Step 5: Rs256JwtSigner 实现**

```java
package com.frame.me.sso.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * RS256 JWT 签名器.
 *
 * <p>SSO 持私钥签发，下游/三方持公钥验签。私钥缺失启动期 fail-fast。</p>
 *
 * @author frame-me
 */
@Slf4j
public class Rs256JwtSigner implements IJwtSigner {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String issuer;

    public Rs256JwtSigner(String privateKeyLocation, String publicKeyLocation, String issuer) {
        this.issuer = issuer;
        this.privateKey = loadPrivateKey(privateKeyLocation);
        this.publicKey = loadPublicKey(publicKeyLocation);
    }

    @Override
    public String sign(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .issuer(issuer)
                .issuedAt(new Date(now))
                .signWith(privateKey, Jwts.SIG.RS256);
        claims.forEach(builder::claim);
        return builder.compact();
    }

    @Override
    public Map<String, Object> verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims;
    }

    private PrivateKey loadPrivateKey(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("me.sso.jwt.private-key 未配置：RS256 私钥为必填项");
        }
        try {
            String pem = readResource(location);
            String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("加载 RS256 私钥失败: " + location, e);
        }
    }

    private PublicKey loadPublicKey(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("me.sso.jwt.public-key 未配置：RS256 公钥为必填项");
        }
        try {
            String pem = readResource(location);
            String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("加载 RS256 公钥失败: " + location, e);
        }
    }

    private String readResource(String location) throws Exception {
        Resource resource = new DefaultResourceLoader().getResource(location);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 6: SsoJwtConfiguration（注册 Bean）**

```java
package com.frame.me.sso.infrastructure.config;

import com.frame.me.sso.infrastructure.jwt.IJwtSigner;
import com.frame.me.sso.infrastructure.jwt.Rs256JwtSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSO JWT 签名器装配.
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
public class SsoJwtConfiguration {

    @Bean
    public IJwtSigner ssoJwtSigner(SsoProperties properties) {
        return new Rs256JwtSigner(
                properties.getJwt().getPrivateKey(),
                properties.getJwt().getPublicKey(),
                properties.getJwt().getIssuer());
    }
}
```

- [ ] **Step 7: 运行测试通过**

```bash
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service test -Dtest=Rs256JwtSignerTest -q
```

Expected: PASS

- [ ] **Step 8: Commit（征求同意）**

---

## Task 6: SsoTokenService

**Files:**
- Create: `.../sso/service/SsoTokenService.java`
- Create: `src/test/.../SsoTokenServiceTest.java`

**Interfaces:**
- Consumes: `IJwtSigner`、`SsoProperties`
- Produces: `SsoTokenService.issue(userId, appId, scope, nonce)` / `parse(token)`

- [ ] **Step 1: 测试**

```java
package com.frame.me.sso;

import com.frame.me.sso.infrastructure.config.SsoProperties;
import com.frame.me.sso.infrastructure.jwt.Rs256JwtSigner;
import com.frame.me.sso.service.SsoTokenService;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SsoTokenServiceTest {

    @Test
    void issueAndParse() {
        Rs256JwtSigner signer = new Rs256JwtSigner(
                "classpath:sso-private.pem", "classpath:sso-public.pem", "frame-me-sso");
        SsoProperties props = new SsoProperties();
        props.getToken().setAccessExpires(Duration.ofHours(2));
        SsoTokenService svc = new SsoTokenService(signer, props);

        String token = svc.issue(1001L, "fm-internal-order", "openid,profile", null);
        Map<String, Object> claims = svc.parse(token);
        assertEquals("1001", claims.get("sub"));
        assertEquals("fm-internal-order", claims.get("aud"));
        assertEquals("openid,profile", claims.get("scope"));
        assertEquals("frame-me-sso", claims.get("iss"));
        assertNotNull(claims.get("exp"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: SsoTokenService 实现**

```java
package com.frame.me.sso.service;

import com.frame.me.sso.infrastructure.config.SsoProperties;
import com.frame.me.sso.infrastructure.jwt.IJwtSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * SSO token 签发与解析服务.
 *
 * <p>签发 RS256 JWT，含 sub(userId)/aud(appId)/scope/iss/exp/iat/nonce。</p>
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class SsoTokenService {

    private final IJwtSigner signer;
    private final SsoProperties properties;

    /**
     * 签发 access token.
     */
    public String issue(Long userId, String appId, String scope, String nonce) {
        long now = System.currentTimeMillis();
        Duration access = properties.getToken().getAccessExpires();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("aud", appId);
        claims.put("scope", scope);
        if (nonce != null && !nonce.isBlank()) {
            claims.put("nonce", nonce);
        }
        claims.put("exp", new Date(now + access.toMillis()));
        return signer.sign(claims);
    }

    /**
     * 解析验签 token.
     */
    public Map<String, Object> parse(String token) {
        return signer.verify(token);
    }
}
```

- [ ] **Step 4: 运行测试通过**

- [ ] **Step 5: Commit（征求同意）**

---

## Task 7: SsoUserService + SsoUserDetailsService

**Files:**
- Create: `.../sso/service/SsoUserService.java`
- Create: `.../sso/service/SsoUserDetailsService.java`

**Interfaces:**
- Produces: `SsoUserService`（用户 CRUD）、`SsoUserDetailsService implements IAuthUserDetailsService`

- [ ] **Step 1: SsoUserService**

```java
package com.frame.me.sso.service;

import com.frame.me.sso.entity.SsoUser;
import com.frame.me.sso.mapper.SsoUserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SSO 用户服务.
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class SsoUserService {

    private final SsoUserMapper ssoUserMapper;

    public SsoUser findByAccount(String account) {
        return ssoUserMapper.selectOneByQuery(QueryWrapper.create().eq("account", account));
    }

    public SsoUser findById(Long id) {
        return ssoUserMapper.selectOneById(id);
    }

    public List<SsoUser> list() {
        return ssoUserMapper.selectListByQuery(QueryWrapper.create());
    }

    public void save(SsoUser user) {
        ssoUserMapper.insert(user);
    }

    public void update(SsoUser user) {
        ssoUserMapper.update(user);
    }
}
```

- [ ] **Step 2: SsoUserDetailsService**

```java
package com.frame.me.sso.service;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.user.User;
import com.frame.me.sso.entity.SsoUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SSO 用户详情服务，实现认证 SPI.
 *
 * <p>把 SsoUser 实体转为基础 User 基类，供 sa-token 会话治理使用。</p>
 *
 * @author frame-me
 */
@Service
@RequiredArgsConstructor
public class SsoUserDetailsService implements IAuthUserDetailsService {

    private final SsoUserService ssoUserService;

    @Override
    public User loadUserByAccount(String account) {
        SsoUser ssoUser = ssoUserService.findByAccount(account);
        return ssoUser == null ? null : toUser(ssoUser);
    }

    @Override
    public User loadUserById(Long id) {
        SsoUser ssoUser = ssoUserService.findById(id);
        return ssoUser == null ? null : toUser(ssoUser);
    }

    private User toUser(SsoUser ssoUser) {
        User user = new User();
        user.setId(ssoUser.getId());
        user.setAccount(ssoUser.getAccount());
        user.setPassword(ssoUser.getPassword());
        user.setNickname(ssoUser.getName());
        user.setStatus("ACTIVE".equals(ssoUser.getStatus())
                ? User.STATUS_ENABLED : User.STATUS_DISABLED);
        return user;
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service -am compile -q
```

- [ ] **Step 4: Commit（征求同意）**

---

## Task 8: SsoAppService + SsoAuthCodeService

**Files:**
- Create: `.../sso/service/SsoAppService.java`
- Create: `.../sso/service/SsoAuthCodeService.java`
- Create: `.../sso/service/SsoLogoutService.java`
- Create: `.../sso/infrastructure/event/UserLogoutEvent.java`
- Create: `src/test/.../SsoAppServiceTest.java`、`SsoAuthCodeServiceTest.java`

**Interfaces:**
- Produces: `SsoAppService`（注册/更新/禁用/密钥）、`SsoAuthCodeService`（签发/消费）、`SsoLogoutService`、`UserLogoutEvent`

- [ ] **Step 1: UserLogoutEvent**

```java
package com.frame.me.sso.infrastructure.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户登出事件（踢人通知）.
 *
 * <p>SSO 踢人时发布，下游订阅后自行处理。transport 由下游选择。</p>
 *
 * @author frame-me
 */
public class UserLogoutEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String appId;
    private LocalDateTime logoutTime;
    private String reason;

    public UserLogoutEvent() {
    }

    public UserLogoutEvent(Long userId, String appId, String reason) {
        this.userId = userId;
        this.appId = appId;
        this.logoutTime = LocalDateTime.now();
        this.reason = reason;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public LocalDateTime getLogoutTime() { return logoutTime; }
    public void setLogoutTime(LocalDateTime logoutTime) { this.logoutTime = logoutTime; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```

- [ ] **Step 2: SsoAppService**

```java
package com.frame.me.sso.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.frame.me.sso.entity.SsoApp;
import com.frame.me.sso.infrastructure.enums.AccessType;
import com.frame.me.sso.infrastructure.enums.AppStatus;
import com.frame.me.sso.infrastructure.enums.TokenEndpointAuthMethod;
import com.frame.me.sso.mapper.SsoAppMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SSO 应用注册服务.
 *
 * <p>INTERNAL 不发 secret（免 aksk），EXTERNAL 生成并加密存储 secret。</p>
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoAppService {

    private final SsoAppMapper ssoAppMapper;

    public SsoApp register(String appName, AccessType accessType, List<String> redirectUris,
                           String scopes, TokenEndpointAuthMethod authMethod) {
        SsoApp app = new SsoApp();
        app.setAppId(generateAppId(accessType));
        app.setAppName(appName);
        app.setAccessType(accessType.name());
        app.setRedirectUris(toJsonArray(redirectUris));
        app.setScopes(scopes);
        app.setTokenEndpointAuthMethod(authMethod.name());
        app.setStatus(AppStatus.ACTIVE.name());

        if (accessType == AccessType.EXTERNAL) {
            String plainSecret = generateSecret();
            app.setAppSecret(encryptSecret(plainSecret));
            app.setAppSecretPlain(plainSecret);
        }
        ssoAppMapper.insert(app);
        return app;
    }

    public SsoApp findByAppId(String appId) {
        return ssoAppMapper.selectOneByQuery(QueryWrapper.create().eq("app_id", appId));
    }

    public List<SsoApp> list() {
        return ssoAppMapper.selectListByQuery(QueryWrapper.create());
    }

    public void update(SsoApp app) {
        ssoAppMapper.update(app);
    }

    public void disable(String appId) {
        SsoApp app = findByAppId(appId);
        if (app != null) {
            app.setStatus(AppStatus.DISABLED.name());
            ssoAppMapper.update(app);
        }
    }

    public String resetSecret(String appId) {
        SsoApp app = findByAppId(appId);
        if (app == null || !AccessType.EXTERNAL.name().equals(app.getAccessType())) {
            throw new IllegalArgumentException("仅 EXTERNAL 应用可重置密钥");
        }
        String plainSecret = generateSecret();
        app.setAppSecret(encryptSecret(plainSecret));
        ssoAppMapper.update(app);
        return plainSecret;
    }

    public boolean verifySecret(SsoApp app, String inputSecret) {
        if (app.getAccessType().equals(AccessType.INTERNAL.name())) {
            return true;
        }
        return encryptSecret(inputSecret).equals(app.getAppSecret());
    }

    private String generateAppId(AccessType accessType) {
        return "fm-" + accessType.name().toLowerCase() + "-" + IdUtil.fastSimpleUUID().substring(0, 8);
    }

    private String generateSecret() {
        return IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
    }

    /**
     * ponytail: 现期 SHA256 单向哈希比对；生产可走 sensi-encrypt 对称加密。
     */
    private String encryptSecret(String plain) {
        return SecureUtil.sha256(plain);
    }

    private String toJsonArray(List<String> uris) {
        StringBuilder sb = new("[");
        for (int i = 0; i < uris.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(uris.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
```

- [ ] **Step 3: SsoAuthCodeService**

```java
package com.frame.me.sso.service;

import cn.hutool.core.util.IdUtil;
import com.frame.me.sso.SsoConstant;
import com.frame.me.sso.entity.SsoAuthCode;
import com.frame.me.sso.infrastructure.config.SsoProperties;
import com.frame.me.sso.mapper.SsoAuthCodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * SSO 授权码服务.
 *
 * <p>Redis 为主存储（短时效、一次性、原子防重放），DB 兜底。</p>
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoAuthCodeService {

    private final SsoAuthCodeMapper ssoAuthCodeMapper;
    private final StringRedisTemplate redisTemplate;
    private final SsoProperties properties;

    public String issue(String appId, Long userId, String scopes, String redirectUri) {
        String code = IdUtil.fastSimpleUUID();
        Duration expires = properties.getAuthCode().getExpires();
        String key = SsoConstant.AUTH_CODE_KEY_PREFIX + code;
        String value = appId + ":" + userId + ":" + scopes + ":" + redirectUri;
        redisTemplate.opsForValue().set(key, value, expires);

        SsoAuthCode entity = new SsoAuthCode();
        entity.setCode(code);
        entity.setAppId(appId);
        entity.setUserId(userId);
        entity.setScopes(scopes);
        entity.setRedirectUri(redirectUri);
        entity.setExpiresAt(LocalDateTime.now().plus(expires));
        entity.setUsed(0);
        ssoAuthCodeMapper.insert(entity);
        return code;
    }

    public CodePayload consume(String code) {
        String key = SsoConstant.AUTH_CODE_KEY_PREFIX + code;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.FALSE.equals(deleted)) {
            return null;
        }
        String[] parts = value.split(":", 4);
        CodePayload payload = new CodePayload();
        payload.appId = parts[0];
        payload.userId = Long.parseLong(parts[1]);
        payload.scopes = parts[2];
        payload.redirectUri = parts.length > 3 ? parts[3] : "";
        return payload;
    }

    public static class CodePayload {
        public String appId;
        public Long userId;
        public String scopes;
        public String redirectUri;
    }
}
```

注：`SsoConstant` 在 `infrastructure` 包，service 引用需 `import com.frame.me.sso.infrastructure.SsoConstant;`。但 `SsoAuthCodeService` 在 `service` 包，包路径是 `com.frame.me.sso.service`，引用 `com.frame.me.sso.infrastructure.SsoConstant` 路径正确。

- [ ] **Step 4: SsoLogoutService**

```java
package com.frame.me.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.sso.infrastructure.event.UserLogoutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * SSO 登出服务（踢人 + 发事件）.
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoLogoutService {

    private final ApplicationEventPublisher eventPublisher;

    public void logout(Long userId, String appId, String reason) {
        try {
            StpUtil.logout(userId);
            log.info("强制登出用户: userId={}, appId={}, reason={}", userId, appId, reason);
        } catch (Exception e) {
            log.warn("强制登出失败: userId={}", userId, e);
            throw e;
        }
        eventPublisher.publishEvent(new UserLogoutEvent(userId, appId, reason));
    }
}
```

- [ ] **Step 5: 测试 SsoAppService**

```java
@Test
void registerInternalNoSecret() {
    // 需 mock mapper 或用 H2 真实库
    SsoApp app = ssoAppService.register("订单服务", AccessType.INTERNAL,
            List.of("http://order.svc/callback"), "openid", TokenEndpointAuthMethod.none);
    assertNull(app.getAppSecret());
}

@Test
void registerExternalHasSecret() {
    SsoApp app = ssoAppService.register("三方 CRM", AccessType.EXTERNAL,
            List.of("https://crm.example.com/cb"), "openid,profile",
            TokenEndpointAuthMethod.client_secret_post);
    assertNotNull(app.getAppSecret());
    assertNotNull(app.getAppSecretPlain());
    assertTrue(ssoAppService.verifySecret(app, app.getAppSecretPlain()));
}
```

- [ ] **Step 6: 运行测试通过**

- [ ] **Step 7: Commit（征求同意）**

---

## Task 9: SsoStpInterface + SsoAuthController

**Files:**
- Create: `.../sso/infrastructure/web/SsoStpInterface.java`
- Create: `.../sso/controller/SsoAuthController.java`
- Create: `.../resources/static/sso-login.html`
- Create: `src/test/.../SsoAuthControllerTest.java`

**Interfaces:**
- Consumes: `SsoAppService`、`SsoAuthCodeService`、`SsoTokenService`、`SsoUserDetailsService`、`SsoProperties`
- Produces: 授权码流程端点

- [ ] **Step 1: SsoStpInterface（infrastructure/web）**

```java
package com.frame.me.sso.infrastructure.web;

import cn.dev33.satoken.stp.StpInterface;
import com.frame.me.sso.entity.SsoUser;
import com.frame.me.sso.mapper.SsoUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * sa-token 权限/角色源，读 SsoUser.roles.
 *
 * @author frame-me
 */
@Component
@RequiredArgsConstructor
public class SsoStpInterface implements StpInterface {

    private final SsoUserMapper ssoUserMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SsoUser user = ssoUserMapper.selectOneById(Long.parseLong(loginId.toString()));
        if (user == null || user.getRoles() == null || user.getRoles().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(user.getRoles().split(","));
    }
}
```

- [ ] **Step 2: sso-login.html**（放 `resources/static/`，内容同前版本）

```html
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><title>SSO 登录</title>
<style>body{font-family:sans-serif;max-width:320px;margin:80px auto}input{width:100%;padding:8px;margin:6px 0;box-sizing:border-box}button{width:100%;padding:10px;background:#2563eb;color:#fff;border:none;cursor:pointer}</style>
</head>
<body>
<h2>SSO 统一登录</h2>
<form id="loginForm">
<input type="text" id="account" placeholder="账号" required>
<input type="password" id="password" placeholder="密码" required>
<button type="submit">登录</button>
</form>
<p id="msg" style="color:red;"></p>
<script>
document.getElementById('loginForm').addEventListener('submit',async(e)=>{
e.preventDefault();
const account=document.getElementById('account').value;
const password=document.getElementById('password').value;
const res=await fetch('/api/sso/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account,password})});
const data=await res.json();
if(data.code===200){const params=new URLSearchParams(location.search);const redirect=params.get('redirect');if(redirect){location.href=redirect;}else{location.href='/api/sso/authorize'+location.search;}}else{document.getElementById('msg').textContent=data.message||'登录失败';}
});
</script>
</body>
</html>
```

- [ ] **Step 3: SsoAuthController**

```java
package com.frame.me.sso.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.auth.web.dto.LoginDTO;
import com.frame.me.base.result.IResult;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.sso.api.dto.TokenRequestDTO;
import com.frame.me.sso.api.vo.JwksVO;
import com.frame.me.sso.api.vo.TokenVO;
import com.frame.me.sso.entity.SsoApp;
import com.frame.me.sso.infrastructure.config.SsoProperties;
import com.frame.me.sso.service.SsoAppService;
import com.frame.me.sso.service.SsoAuthCodeService;
import com.frame.me.sso.service.SsoTokenService;
import com.frame.me.sso.service.SsoUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * SSO 授权码流程端点.
 *
 * @author frame-me
 */
@Slf4j
@RestController
@RequestMapping("${me.sso.path:/api/sso}")
@RequiredArgsConstructor
public class SsoAuthController {

    private final SsoAppService ssoAppService;
    private final SsoAuthCodeService ssoAuthCodeService;
    private final SsoTokenService ssoTokenService;
    private final SsoUserDetailsService ssoUserDetailsService;
    private final SsoProperties properties;

    @GetMapping("/authorize")
    public void authorize(@RequestParam String appId,
                          @RequestParam String redirectUri,
                          @RequestParam(required = false, defaultValue = "openid") String scope,
                          @RequestParam(required = false) String nonce,
                          HttpServletResponse response) throws IOException {
        SsoApp app = ssoAppService.findByAppId(appId);
        if (app == null || !"ACTIVE".equals(app.getStatus())) {
            response.sendError(400, "应用不存在或已禁用");
            return;
        }
        if (!isRedirectAllowed(app, redirectUri)) {
            response.sendError(400, "redirect_uri 不在白名单");
            return;
        }
        if (!StpUtil.isLogin()) {
            response.sendRedirect("/sso-login.html?redirect=" +
                    URLEncoder.encode("/api/sso/authorize?" +
                            buildQuery(appId, redirectUri, scope, nonce), StandardCharsets.UTF_8));
            return;
        }
        Long userId = StpUtil.getLoginIdAsLong();
        String code = ssoAuthCodeService.issue(appId, userId, scope, redirectUri);
        String sep = redirectUri.contains("?") ? "&" : "?";
        response.sendRedirect(redirectUri + sep + "code=" + code);
    }

    @GetMapping("/login-page")
    public void loginPage(HttpServletResponse response) throws IOException {
        if (properties.getLoginPage().isEnabled()) {
            response.sendRedirect("/sso-login.html");
        } else {
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":200,\"message\":\"请 POST /api/sso/login\"}");
        }
    }

    @PostMapping("/login")
    public IResult<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        var user = ssoUserDetailsService.loadUserByAccount(dto.getAccount());
        if (user == null || !ssoUserDetailsService.matches(dto.getPassword(), user.getPassword())) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == User.STATUS_DISABLED) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "账号已禁用");
        }
        StpUtil.login(user.getId());
        TokenVO vo = new TokenVO();
        vo.setAccessToken(StpUtil.getTokenValue());
        return IResult.ok(vo);
    }

    @PostMapping("/token")
    public IResult<TokenVO> token(@RequestBody TokenRequestDTO req) {
        SsoAuthCodeService.CodePayload payload = ssoAuthCodeService.consume(req.getCode());
        if (payload == null) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "授权码无效或已使用");
        }
        if (!payload.appId.equals(req.getAppId())) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "app_id 与授权码不匹配");
        }
        SsoApp app = ssoAppService.findByAppId(req.getAppId());
        if (app == null || !"ACTIVE".equals(app.getStatus())) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "应用不存在或已禁用");
        }
        if (!payload.redirectUri.equals(req.getRedirectUri())) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "redirect_uri 不匹配");
        }
        if (!ssoAppService.verifySecret(app, req.getAppSecret())) {
            return IResult.fail(ResultCode.UNAUTHORIZED, "密钥校验失败");
        }
        String token = ssoTokenService.issue(payload.userId, req.getAppId(),
                payload.scopes, req.getNonce());
        TokenVO vo = new TokenVO();
        vo.setAccessToken(token);
        return IResult.ok(vo);
    }

    @PostMapping("/refresh")
    public IResult<TokenVO> refresh(@RequestHeader("Authorization") String auth) {
        String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        Map<String, Object> claims = ssoTokenService.parse(token);
        Long userId = Long.parseLong(claims.get("sub").toString());
        String appId = claims.get("aud").toString();
        String scope = claims.get("scope").toString();
        String newToken = ssoTokenService.issue(userId, appId, scope, null);
        TokenVO vo = new TokenVO();
        vo.setAccessToken(newToken);
        return IResult.ok(vo);
    }

    @GetMapping("/logout")
    public IResult<Boolean> logout() {
        StpUtil.logout();
        return IResult.ok(true);
    }

    @GetMapping("/jwks")
    public IResult<JwksVO> jwks() {
        JwksVO vo = new JwksVO();
        vo.setKty("RSA");
        vo.setAlg("RS256");
        vo.setUse("sig");
        vo.setLocation("/api/sso/jwks/public-key");
        return IResult.ok(vo);
    }

    @GetMapping("/jwks/public-key")
    public void publicKey(HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        response.getWriter().write(readPublicKey());
    }

    private boolean isRedirectAllowed(SsoApp app, String redirectUri) {
        if (app.getRedirectUris() == null || app.getRedirectUris().isBlank()) {
            return false;
        }
        return app.getRedirectUris().contains(redirectUri);
    }

    private String buildQuery(String appId, String redirectUri, String scope, String nonce) {
        StringBuilder sb = new StringBuilder("appId=").append(appId)
                .append("&redirectUri=").append(redirectUri)
                .append("&scope=").append(scope);
        if (nonce != null) sb.append("&nonce=").append(nonce);
        return sb.toString();
    }

    private String readPublicKey() {
        try {
            var res = new org.springframework.core.io.DefaultResourceLoader()
                    .getResource(properties.getJwt().getPublicKey());
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
```

注：Controller 用 api 模块的 `TokenRequestDTO`/`TokenVO`/`JwksVO`，不再用 auth 模块的 `TokenVO`。

- [ ] **Step 4: 端点测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
class SsoAuthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void authorizeRedirectsToLoginWhenNotLoggedIn() throws Exception {
        // 需先注册一个 app 到测试库
        mvc.perform(get("/api/sso/authorize")
                .param("appId", "fm-internal-test")
                .param("redirectUri", "http://order.svc/cb"))
           .andExpect(status().is3xxRedirection());
    }

    @Test
    void tokenWithInvalidCodeFails() throws Exception {
        mvc.perform(post("/api/sso/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"invalid\",\"app_id\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **Step 5: 运行测试通过**

- [ ] **Step 6: Commit（征求同意）**

---

## Task 10: SsoAdminController

**Files:**
- Create: `.../sso/controller/SsoAdminController.java`

**Interfaces:**
- Produces: 管理端点（`@SaCheckRole("admin")` 保护）

- [ ] **Step 1: SsoAdminController**

```java
package com.frame.me.sso.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.frame.me.base.result.IResult;
import com.frame.me.sso.api.dto.RegisterAppDTO;
import com.frame.me.sso.api.vo.AppVO;
import com.frame.me.sso.entity.SsoApp;
import com.frame.me.sso.infrastructure.enums.AccessType;
import com.frame.me.sso.infrastructure.enums.TokenEndpointAuthMethod;
import com.frame.me.sso.service.SsoAppService;
import com.frame.me.sso.service.SsoLogoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SSO 管理端点（@SaCheckRole admin 保护）.
 *
 * @author frame-me
 */
@RestController
@RequestMapping("${me.sso.path:/api/sso}/admin")
@RequiredArgsConstructor
public class SsoAdminController {

    private final SsoAppService ssoAppService;
    private final SsoLogoutService ssoLogoutService;

    @SaCheckRole("admin")
    @PostMapping("/app")
    public IResult<AppVO> register(@RequestBody RegisterAppDTO dto) {
        AccessType type = AccessType.valueOf(dto.getAccessType());
        TokenEndpointAuthMethod method = TokenEndpointAuthMethod.valueOf(
                dto.getTokenEndpointAuthMethod() != null ? dto.getTokenEndpointAuthMethod()
                        : (type == AccessType.INTERNAL ? "none" : "client_secret_post"));
        SsoApp app = ssoAppService.register(
                dto.getAppName(), type, dto.getRedirectUris(),
                dto.getScopes() != null ? dto.getScopes() : "openid", method);
        return IResult.ok(toVO(app));
    }

    @SaCheckRole("admin")
    @PutMapping("/app/{appId}")
    public IResult<Boolean> update(@PathVariable String appId, @RequestBody SsoApp app) {
        app.setAppId(appId);
        ssoAppService.update(app);
        return IResult.ok(true);
    }

    @SaCheckRole("admin")
    @PostMapping("/app/{appId}/reset-secret")
    public IResult<AppVO> resetSecret(@PathVariable String appId) {
        String secret = ssoAppService.resetSecret(appId);
        SsoApp app = ssoAppService.findByAppId(appId);
        AppVO vo = toVO(app);
        vo.setAppSecret(secret);
        return IResult.ok(vo);
    }

    @SaCheckRole("admin")
    @DeleteMapping("/app/{appId}")
    public IResult<Boolean> disable(@PathVariable String appId) {
        ssoAppService.disable(appId);
        return IResult.ok(true);
    }

    @SaCheckRole("admin")
    @GetMapping("/apps")
    public IResult<List<AppVO>> list() {
        return IResult.ok(ssoAppService.list().stream().map(this::toVO).collect(Collectors.toList()));
    }

    @SaCheckRole("admin")
    @PostMapping("/user/{userId}/logout")
    public IResult<Boolean> forceLogout(@PathVariable Long userId,
                                        @RequestParam(required = false) String appId,
                                        @RequestParam(defaultValue = "admin") String reason) {
        ssoLogoutService.logout(userId, appId, reason);
        return IResult.ok(true);
    }

    private AppVO toVO(SsoApp app) {
        AppVO vo = new AppVO();
        vo.setAppId(app.getAppId());
        vo.setAppName(app.getAppName());
        vo.setAccessType(app.getAccessType());
        vo.setStatus(app.getStatus());
        return vo;
    }
}
```

- [ ] **Step 2: 管理鉴权测试**

```java
@Test
void adminEndpointRejectsNonAdmin() throws Exception {
    mvc.perform(post("/api/sso/admin/app")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
       .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 3: 运行测试通过**

- [ ] **Step 4: Commit（征求同意）**

---

## Task 11: 上下文加载测试 + 全量验证

**Files:**
- Create: `src/test/.../SsoContextLoadTest.java`
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: test 配置**

`src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:sso-test;MODE=MySQL
    username: sa
    password:
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  data:
    redis:
      host: localhost
      port: 6379

me:
  sso:
    jwt:
      private-key: classpath:sso-private.pem
      public-key: classpath:sso-public.pem
  auth:
    sa-token:
      jwt:
        enabled: false
```

- [ ] **Step 2: 上下文加载测试**

```java
package com.frame.me.sso;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class SsoContextLoadTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 3: 运行全量测试**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home
./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service -am test -q
```

Expected: 所有测试 PASS

- [ ] **Step 4: Commit（征求同意）**

---

## Task 12: 文档同步

- [ ] **Step 1: docs/modules.md** 新增 frame-me-sso / frame-me-sso-api / frame-me-sso-service 章节
- [ ] **Step 2: docs/reference.md** 补充 SSO 关键类索引
- [ ] **Step 3: docs/architecture.md** 服务边界补充 SSO
- [ ] **Step 4: docs/conventions.md** 补充 SSO 表前缀/claims/infrastructure 包约定
- [ ] **Step 5: docs/guides/sso.md** 新建接入指南（内部/外部接入、下游验签、踢人三档）
- [ ] **Step 6: CLAUDE.md** 补充启动命令 `./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service spring-boot:run`
- [ ] **Step 7: Commit（征求同意）**

---

## Verification（端到端验证）

1. **启动 SSO 服务**：
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home
   ./mvnw -pl frame-me-launcher/frame-me-sso/frame-me-sso-service spring-boot:run
   ```

2. **注册内部应用**（先手动插入 admin 用户到 `fm_sso_user`，roles 含 `admin`）
3. **授权码流程** → **换 token** → **验签 JWT**
4. **踢人** → 验证 `UserLogoutEvent`
5. **JWKS 端点** → 下载公钥验签

---

## Self-Review 记录

- **Spec 覆盖**：设计文档所有章节均有对应 Task
- **包结构调整**：应用类型工程用 `infrastructure` 包包裹 Constant/config/jwt/enums/event/web，controller/service/entity/mapper 顶层保留——已反映到所有 Task 文件路径
- **api 契约模块**：`frame-me-sso-api` 存放 `@HttpExchange` 契约 + dto/vo，参考 tester 结构
- **类型一致性**：`IJwtSigner.sign/verify`、`SsoTokenService.issue/parse`、`SsoAuthCodeService.issue/consume`、`SsoAppService.register/verifySecret/resetSecret` 签名一致；api 模块 `TokenVO`/`TokenRequestDTO`/`AppVO`/`JwksVO` 在 Controller/Service 间引用一致
- **修正点**：Controller 用 api 模块的 VO/DTO（`com.frame.me.sso.api.vo.TokenVO`），不再用 auth 模块的 `TokenVO`
