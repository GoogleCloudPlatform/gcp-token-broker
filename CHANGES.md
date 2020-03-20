## 0.10.0 (XXX)

- Disabled credential access boundary by default.
- Added new base client library: `broker-client-lib`.
- Renamed `broker-connector` package to `broker-hadoop-connector`.
- Added `CheckConfig` command.

## 0.9.2 (March 9, 2020)

- Added support for credentials access boundary.
- Simplified Kubernetes configuration for demo deployment.

## 0.9.1 (March 5, 2020)

- Fixed an issue with the `broker-server` package assembly.

## 0.9.0 (March 5, 2020)

- Added integration with Secret Manager.
- Added new means of passing settings to the application: base64-encoded or hosted on Cloud Storage.
- Added Datastore cache backend.
- Added ability to store data encryption key (DEK) on the file system.

## 0.8.0 (February 20, 2020)

- Added the `HybridProvider`, which is now the default provider.
- Reformatted the `proxy-users` setting. Proxy users can now also be downscoped to a list of impersonated users or
  groups.
- Introduced the concept of user mappers. The default user mapper is `KerberosUserMapper`. You must now explicitly
  provide user mapping rules by configuring the `user-mapping.rules` setting.
- Removed the `ShadowServiceAccountProvider` (replaced with the more generic `ServiceAccountProvider`).
- Removed the `provider.shadow-service-accounts.project` and `provider.shadow-service-accounts.username-pattern`
  settings. Instead, you must now configure user mapping rules for the `KerberosUserMapper`.

## 0.7.0 (January 24, 2019)

- Changed scopes' format from comma-separated list string to proper list of strings in protocol buffers and in
  `scopes.whitelist` setting.

## 0.6.0 (December 13, 2019)

- Moved Authorizer and Broker server code to the `com.google.cloud.broker.apps` package. 
- Renamed all settings to fit better Typesafe Config conventions. Settings can now be passed as configuration files.
- Moved providers to the `com.google.cloud.broker.apps.brokerserver.accesstokens.providers` package.
- Renamed the broker service's Maven artifact from `broker` to `broker-server`.
- Decoupled the broker URI from the broker kerberos principal name. This includes the following changes:
  * On the broker side:
    - The `BROKER_SERVICE_HOSTNAME` and `BROKER_SERVICE_NAME` are removed.
    - The `KEYTABS_PATH` setting is replaced by `authentication.spnego.keytabs`.
    - The new `authentication.spnego.keytabs` setting is a list of keytab/principal pairs. Those are used by the broker
      to log in at launch time.
  * On the connector side:
    - The `gcp.token.broker.{realm|servicename}` and `gcp.token.broker.uri.hostname` are removed in favor of a
      new `gcp.token.broker.kerberos.principal` setting.
    - The `gcp.token.broker.uri.{hostname|port}` and `gcp.token.broker.tls.enabled` settings are removed in
      favor of a new `gcp.token.broker.uri` setting.

## 0.5.0 (November 22, 2019)

- Rewrote Authorizer app from Python to Java.
- Refactored settings system to use the Typesafe Config library.
- Made the Cloud KMS encryption backend use envelope encryption. The backend now requires
  both the `ENCRYPTION_KEK_URI` and `ENCRYPTION_DEK_URI` settings. To generate the data
  encryption key (DEK), use the newly added `GenerateDEK` command.
- Added `TLS_CRT_PATH` setting to allow passing the broker server's TLS certificate as a file.
- Moved the `InitializeDatabase` command to the `com.google.cloud.broker.database` package.
- Renamed `CLIENT_SECRET_PATH` setting to `OAUTH_CLIENT_SECRET_JSON_PATH`.
- Introduced the `OAUTH_CLIENT_ID` and `OAUTH_CLIENT_SECRET` settings as an alternative
  to `OAUTH_CLIENT_SECRET_JSON_PATH`.


## 0.4.1 (August 16, 2019)

- Made shadow service account's username pattern configurable.

## 0.4.0 (August 8, 2019)

- Restructured the Java codebase by splitting off some components into separate modules:
  `broker` (the main broker service), `cache-backend-redis`, `database-backend-cloud-datastore`,
  `database-backend-jdbc`, `encryption-backend-cloud-kms`, and `broker-core` (code shared by all the
  aforementioned packages).
- Added support for MySQL and MariaDB database backends (via `JDBCBackend`).
- Modified Session primary key data type from integer to string.
- Add support for pluggable authentication backends, defaults to SPNEGO authentication.

## 0.3.2 (July 25, 2019)

- Made `RefreshTokenProvider` work with all database backends instead of just Cloud Datastore.

## 0.3.1 (July 1, 2019)

- Added `gcp.token.broker.realm` setting in the broker connector to control
  the broker principal's realm.
- Fixed a bug in the Cloud Datastore database backend.

## 0.3.0 (June 28, 2019)

- Added `JDBCDatabaseBackend` to enable storing state in relational databases.
- Added `DummyCache`, `DummyEncryptionBackend`, and an experimental
  `JSONFileCredentialsProvider` to make simple deployments easier.
  These backends are for development and testing only, and should *not*
  be used in production.

## 0.2.0 (May 17, 2019)

- Rewrote the broker server application from Python to Java.
- Simplified KDC trust topology. Now the Broker service does not
  require its own KDC and does not require any connectivity with the
  origin KDC or AD. Also, it is now required to upload a separate
  keytab for each realm so the broker service can authenticate users.

## 0.1.0 (April 5, 2019)

Initial release.
