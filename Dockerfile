FROM openjdk:17.0.1-jdk-slim
MAINTAINER isKONSTANTIN <me@knst.su>

RUN apt-get update && \
    apt-get install -y libfreetype6 fonts-dejavu-core fontconfig libx11-6 libxext6 libxrender1 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /tg_ai

COPY ./Knst-Telegram-AI.jar ./

ENTRYPOINT exec java $JAVA_OPTS -jar Knst-Telegram-AI.jar