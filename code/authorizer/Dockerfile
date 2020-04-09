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

COPY ./code/authorizer/install.sh /base/code/authorizer/install.sh

RUN /base/code/authorizer/install.sh

COPY ./code/built-jars/authorizer-*-jar-with-dependencies.jar /classpath/authorizer.jar
COPY ./code/built-jars/database-backend-jdbc-*-jar-with-dependencies.jar /classpath/database-backend-jdbc.jar
COPY ./code/built-jars/database-backend-cloud-datastore-*-jar-with-dependencies.jar /classpath/database-backend-cloud-datastore.jar
COPY ./code/built-jars/encryption-backend-cloud-kms-*-jar-with-dependencies.jar /classpath/encryption-backend-cloud-kms.jar

WORKDIR /base/code/authorizer

ENTRYPOINT ["java", "-cp", "/classpath/*", "com.google.cloud.broker.apps.authorizer.Authorizer"]