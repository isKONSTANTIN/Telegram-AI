FROM openjdk:17.0.1-jdk-slim
MAINTAINER isKONSTANTIN <me@knst.su>

WORKDIR /tg_ai

COPY ./Knst-Telegram-AI.jar ./

ENTRYPOINT exec java $JAVA_OPTS -jar Knst-Telegram-AI.jar