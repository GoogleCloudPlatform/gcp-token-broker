FROM ubuntu:18.04

COPY ./apps/authorizer/install.sh /base/apps/authorizer/install.sh

RUN /base/apps/authorizer/install.sh

COPY ./apps/authorizer/target/authorizer-*-jar-with-dependencies.jar /classpath/authorizer.jar
COPY ./apps/extensions/database/cloud-datastore/target/database-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/database-backend-cloud-datastore.jar
COPY ./apps/extensions/encryption/cloud-kms/target/encryption-backend-cloud-kms-*-jar-with-dependencies.jar /classpath/encryption-backend-cloud-kms.jar

WORKDIR /base/apps/authorizer