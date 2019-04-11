FROM ubuntu:18.04

COPY ./apps/authorizer/install.sh /base/apps/authorizer/install.sh

COPY ./apps/authorizer/requirements.txt /base/apps/authorizer/requirements.txt

RUN /base/apps/authorizer/install.sh

COPY ./apps/authorizer /base/apps/authorizer

WORKDIR /base/apps/authorizer