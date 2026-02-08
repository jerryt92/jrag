FROM alpine/git AS ui
ARG JRAG_UI_REPO=https://github.com/jerryt92/jrag-ui.git
ARG JRAG_UI_BRANCH=dist
ARG UPDATE_UI=true
RUN if [ "${UPDATE_UI}" = "true" ]; then \
      git clone -b "${JRAG_UI_BRANCH}" "${JRAG_UI_REPO}" /dist; \
    else \
      mkdir -p /dist; \
    fi

FROM maven:3.8.8-amazoncorretto-21-debian AS build
WORKDIR /workspace
ARG MAVEN_SETTINGS=/workspace/maven-settings.xml
ARG UPDATE_UI=true
COPY pom.xml /workspace/
COPY docker/maven-settings.xml /workspace/maven-settings.xml
COPY jrag-client/pom.xml /workspace/jrag-client/
COPY jrag-model/pom.xml /workspace/jrag-model/
COPY jrag-server/pom.xml /workspace/jrag-server/
COPY jrag-starter/pom.xml /workspace/jrag-starter/
COPY . /workspace
COPY --from=ui /dist /tmp/jrag-ui-dist
RUN if [ "${UPDATE_UI}" = "true" ]; then \
      rm -rf /workspace/jrag-starter/src/main/resources/dist; \
      mkdir -p /workspace/jrag-starter/src/main/resources/dist; \
      cp -R /tmp/jrag-ui-dist/. /workspace/jrag-starter/src/main/resources/dist; \
    else \
      echo "Skip UI update, use existing dist in source tree."; \
    fi
RUN --mount=type=cache,target=/root/.m2 mvn -s "${MAVEN_SETTINGS}" -pl jrag-starter -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /opt/jrag
ENV JRAG_CONFIG_PATH=/config/
COPY --from=build /workspace/jrag-starter/target/*.tar.gz /tmp/jrag.tar.gz
RUN mkdir -p /opt && tar -xzf /tmp/jrag.tar.gz -C /opt && rm /tmp/jrag.tar.gz
EXPOSE 30110
ENTRYPOINT ["sh", "-c", "exec java -cp \"/opt/jrag/classes:/opt/jrag/lib/*\" $JAVA_OPTS io.github.jerryt92.jrag.JragStarterMain"]
