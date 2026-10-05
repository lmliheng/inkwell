# JScreator Java 微服务 —— 单镜像多服务
#
# 为什么是一个镜像装 5 个 jar：这台机器只有 2 核，5 个服务各构建一次的代价太高。
# 构建阶段跑一遍 Maven 产出全部 jar，运行阶段按启动参数选一个跑。

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# 先只拷贝 POM，让依赖层能被缓存（改代码不必重新下载依赖）
COPY pom.xml ./
COPY jscreator-common/pom.xml jscreator-common/
COPY jscreator-common-web/pom.xml jscreator-common-web/
COPY jscreator-gateway/pom.xml jscreator-gateway/
COPY jscreator-auth/pom.xml jscreator-auth/
COPY jscreator-content/pom.xml jscreator-content/
COPY jscreator-social/pom.xml jscreator-social/
COPY jscreator-system/pom.xml jscreator-system/
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY jscreator-common/src jscreator-common/src
COPY jscreator-common-web/src jscreator-common-web/src
COPY jscreator-gateway/src jscreator-gateway/src
COPY jscreator-auth/src jscreator-auth/src
COPY jscreator-content/src jscreator-content/src
COPY jscreator-social/src jscreator-social/src
COPY jscreator-system/src jscreator-system/src
RUN mvn -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /src/jscreator-gateway/target/jscreator-gateway-1.0.0.jar /app/gateway.jar
COPY --from=build /src/jscreator-auth/target/jscreator-auth-1.0.0.jar       /app/auth.jar
COPY --from=build /src/jscreator-content/target/jscreator-content-1.0.0.jar /app/content.jar
COPY --from=build /src/jscreator-social/target/jscreator-social-1.0.0.jar   /app/social.jar
COPY --from=build /src/jscreator-system/target/jscreator-system-1.0.0.jar   /app/system.jar

COPY deploy/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8088
ENTRYPOINT ["/app/entrypoint.sh"]
