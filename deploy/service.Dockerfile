FROM eclipse-temurin:11-jre

ARG JAR_FILE
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

WORKDIR /app
COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
