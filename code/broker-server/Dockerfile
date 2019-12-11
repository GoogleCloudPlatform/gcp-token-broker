FROM ubuntu:18.04

COPY ./code/broker-server/install.sh /base/code/broker-server/install.sh

RUN /base/code/broker-server/install.sh

COPY ./code/broker-server/target/broker-server-*-jar-with-dependencies.jar /classpath/broker-server.jar
COPY ./code/extensions/caching/redis/target/cache-backend-redis-*-jar-with-dependencies.jar /classpath/cache-backend-redis.jar
COPY ./code/extensions/database/cloud-datastore/target/database-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/database-backend-cloud-datastore.jar
COPY ./code/extensions/encryption/cloud-kms/target/encryption-backend-cloud-kms-*-jar-with-dependencies.jar /classpath/encryption-backend-cloud-kms.jar

WORKDIR /base/code/broker