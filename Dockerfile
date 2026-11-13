## ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

COPY hm-dianping/pom.xml hm-dianping/
COPY hm-dianping/src hm-dianping/src

RUN mvn -B -f hm-dianping/pom.xml clean package -Dmaven.test.skip=true

## ---------- 运行阶段 ----------
FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app
COPY --from=build /build/hm-dianping/target/hm-dianping-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
