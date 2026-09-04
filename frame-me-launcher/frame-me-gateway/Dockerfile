# 构建上下文 = frame-me-parent/frame-me-launcher/frame-me-gateway/
# 前置：mvn -B clean package -pl frame-me-launcher/frame-me-gateway -am -Dmaven.test.skip=true
#       （在 frame-me-parent/ 目录执行，云效"构建"阶段）
#
# 构建期变量（云效流水线通过 docker build --build-arg 注入，对齐 EVALUATION-DAILY 的 ARGS 约定）
# ARG 必须在 FROM 之后声明才能在后续指令中引用（FROM 之前的 ARG 只供 FROM 行使用）

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
# COPY 精确文件名：pom 已配 finalName=app，避免 *-sources.jar 参与 *.jar 通配导致多源 COPY 失败
COPY target/app.jar app.jar

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
