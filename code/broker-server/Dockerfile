# Copyright 2020 Google LLC
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM ubuntu:18.04

COPY ./code/broker-server/install.sh /base/code/broker-server/install.sh

RUN /base/code/broker-server/install.sh

COPY ./code/built-jars/broker-server-*-jar-with-dependencies.jar /classpath/broker-server.jar
COPY ./code/built-jars/cache-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/cache-backend-cloud-datastore.jar
COPY ./code/built-jars/cache-backend-redis-*-jar-with-dependencies.jar /classpath/cache-backend-redis.jar
COPY ./code/built-jars/database-backend-jdbc-*-jar-with-dependencies.jar /classpath/database-backend-jdbc.jar
COPY ./code/built-jars/database-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/database-backend-cloud-datastore.jar
COPY ./code/built-jars/encryption-backend-cloud-kms-*-jar-with-dependencies.jar /classpath/encryption-backend-cloud-kms.jar

WORKDIR /base/code/broker

ENTRYPOINT ["java", "-cp", "/classpath/*", "com.google.cloud.broker.apps.brokerserver.BrokerServer"]