# 多阶段构建：阶段1 用 maven:25 编译，阶段2 用 eclipse-temurin:25-jre-alpine 运行
# 构建上下文 = frame-me-parent/（仓库根，mvn -pl -am 需要父 pom + 依赖模块）
# 云效流水线只做 docker build（DockerBuildPushACREE），不再单独执行 mvn package
#
# 构建期变量（云效流水线通过 docker build --build-arg 注入，对齐 EVALUATION-DAILY 的 ARGS 约定）
# ARG 必须在 FROM 之后声明才能在后续指令中引用（FROM 之前的 ARG 只供 FROM 行使用）

# ── 阶段1：编译（JDK 25 + Maven 3.9）──
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
RUN mvn -B clean package -pl frame-me-launcher/frame-me-gateway -am -Dmaven.test.skip=true

# ── 阶段2：运行镜像（JRE 25，无 JDK/构建工具）──
FROM eclipse-temurin:25-jre-alpine

ARG PROJECT_NAME
ARG PROJECT_VERSION
ARG ENV
ARG BUILD_DATE
ARG GIT_COMMIT

# 必填校验：云效忘传 build args 时构建失败，避免静默产出元数据空值的镜像
RUN test -n "$PROJECT_NAME" && test -n "$PROJECT_VERSION" && test -n "$ENV" \
    && test -n "$BUILD_DATE" && test -n "$GIT_COMMIT" \
    || (echo "ERROR: build args PROJECT_NAME/PROJECT_VERSION/ENV/BUILD_DATE/GIT_COMMIT must not be empty" && exit 1)

RUN apk add --no-cache curl tzdata
ENV TZ=Asia/Shanghai

# 运行期可见（容器内可通过 System.getenv() 读取）
ENV PROJECT_NAME=${PROJECT_NAME} \
    PROJECT_VERSION=${PROJECT_VERSION} \
    ENV=${ENV}

WORKDIR /app
# 从阶段1拷贝编译产物（pom 已配 finalName=app，避免 *-sources.jar 参与 *.jar 通配导致多源 COPY 失败）
COPY --from=build /build/frame-me-launcher/frame-me-gateway/target/app.jar app.jar

# OCI 镜像元数据（构建期注入，便于追溯来源）
LABEL org.opencontainers.image.title="${PROJECT_NAME}" \
      org.opencontainers.image.version="${PROJECT_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT}"

EXPOSE 10030 10031 5050
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar", \
  "--spring.profiles.active=${ENV}"]
