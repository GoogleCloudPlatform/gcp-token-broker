#!/bin/bash

export AUTHORIZER_KEYTAB="authorizer.keytab"
export AUTHORIZER_PRINCIPAL="HTTP/localhost@EXAMPLE.COM"
export AUTHORIZER_HOST="localhost"
export AUTHORIZER_PORT="8080"
export OAUTH_CALLBACK_URI="http://localhost:8080/oauth2callback"
export OAUTH_CLIENT_ID="changeit"
export OAUTH_CLIENT_SECRET="changeit"
export AUTHORIZER_ENABLE_SPNEGO="true"
export ENCRYPTION_BACKEND="com.google.cloud.broker.encryption.backends.CloudKMSBackend"
export DATABASE_BACKEND="com.google.cloud.broker.database.backends.JDBCBackend"
export DATABASE_JDBC_URL="jdbc:h2:mem:;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"

java -Xms512m -Xmx512m -cp authorizer-0.4.1.jar:broker-0.4.1-jar-with-dependencies.jar com.google.cloud.broker.authorization.Authorizer
