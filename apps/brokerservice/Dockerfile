FROM ubuntu:18.04

COPY ./apps/brokerservice/install.sh /base/apps/brokerservice/install.sh

COPY ./apps/brokerservice/requirements.txt /base/apps/brokerservice/requirements.txt

RUN /base/apps/brokerservice/install.sh

COPY ./apps/brokerservice /base/apps/brokerservice

COPY ./apps/common /base/apps/common

WORKDIR /base/apps/brokerservice