# 是否在 docker 中编译Java 项目，如果为 false，则需要在宿主机中先编译
ARG BUILD_MVN_ON_DOCKER=true
FROM maven:3.8.8-amazoncorretto-21-debian AS build-env
WORKDIR /workspace
ARG MAVEN_SETTINGS=/workspace/maven-settings.xml
COPY pom.xml /workspace/
COPY docker/maven-settings.xml /workspace/maven-settings.xml
COPY jrag-client/pom.xml /workspace/jrag-client/
COPY jrag-model/pom.xml /workspace/jrag-model/
COPY jrag-server/pom.xml /workspace/jrag-server/
COPY jrag-starter/pom.xml /workspace/jrag-starter/
COPY . /workspace
RUN --mount=type=cache,target=/root/.m2 mvn -s "${MAVEN_SETTINGS}" -pl jrag-starter -am -DskipTests package

# 如果 BUILD_MVN_ON_DOCKER 为 true，则从 build-env 阶段获取文件
FROM build-env AS source-true
RUN cp /workspace/jrag-starter/target/*.tar.gz /tmp/jrag.tar.gz

# 如果 BUILD_MVN_ON_DOCKER 为 false，则从宿主机 context 获取文件
FROM scratch AS source-false
ONBUILD COPY jrag-starter/target/*.tar.gz /tmp/jrag.tar.gz

# 根据参数决定使用哪个源阶段
FROM source-${BUILD_MVN_ON_DOCKER} AS final-source

FROM eclipse-temurin:21-jre
WORKDIR /opt/jrag

# 从上一个动态选择的阶段中复制文件
COPY --from=final-source /tmp/jrag.tar.gz /tmp/jrag.tar.gz

RUN mkdir -p /opt && tar -xzf /tmp/jrag.tar.gz -C /opt && rm /tmp/jrag.tar.gz
EXPOSE 30110
ENTRYPOINT ["sh", "-c", "exec java -cp \"/opt/jrag/classes:/opt/jrag/lib/*\" $JAVA_OPTS io.github.jerryt92.jrag.JragStarterMain"]