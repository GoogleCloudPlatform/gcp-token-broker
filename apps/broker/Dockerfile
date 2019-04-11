FROM ubuntu:18.04

COPY ./apps/broker/install.sh /base/apps/broker/install.sh

RUN /base/apps/broker/install.sh

COPY ./apps/broker/target/broker-*-jar-with-dependencies.jar /base/apps/broker/target/broker.jar

WORKDIR /base/apps/broker