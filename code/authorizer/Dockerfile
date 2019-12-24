FROM ubuntu:18.04

COPY ./code/authorizer/install.sh /base/code/authorizer/install.sh

RUN /base/code/authorizer/install.sh

COPY ./code/authorizer/target/authorizer-*-jar-with-dependencies.jar /classpath/authorizer.jar
COPY ./code/extensions/database/cloud-datastore/target/database-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/database-backend-cloud-datastore.jar
COPY ./code/extensions/encryption/cloud-kms/target/encryption-backend-cloud-kms-*-jar-with-dependencies.jar /classpath/encryption-backend-cloud-kms.jar

WORKDIR /base/code/authorizer