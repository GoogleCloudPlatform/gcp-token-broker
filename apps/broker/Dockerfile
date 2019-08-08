FROM ubuntu:18.04

COPY ./apps/broker/install.sh /base/apps/broker/install.sh

RUN /base/apps/broker/install.sh

COPY ./apps/broker/target/broker-*-jar-with-dependencies.jar /classpath/broker.jar
COPY ./apps/extensions/caching/redis/target/cache-backend-redis-*-jar-with-dependencies.jar /classpath/cache-backend-redis.jar
COPY ./apps/extensions/database/cloud-datastore/target/database-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/database-backend-cloud-datastore.jar
COPY ./apps/extensions/encryption/cloud-kms/target/encryption-backend-cloud-kms-*-jar-with-dependencies.jar /classpath/encryption-backend-cloud-kms.jar

WORKDIR /base/apps/broker