This page lists the settings available for the broker application, in alphabetical order.

## Available settings

### `authentication.backend`

Default: `com.google.cloud.broker.authentication.backends.SpnegoAuthenticator`

[Authentication](authentication.md) backend class.

### `authentication.spnego.keytabs`

List of principal/keytab pairs for the broker service to log in with. For example:

```
[{principal=broker/example.com@MYREALM, keytab=/etc/security/broker.keytab}, {principal=broker/foo@BAR, keytab=/etc/security/broker-foobar.keytab}]
```

### `database.backend`

Default: `com.google.cloud.broker.database.backends.CloudDatastoreBackend`

[Database](database.md) backend class.

### `database.jdbc.driver-url`

JDBC url for the relational database. Only necessary if you choose to use the [JDBC database backend](database.md#jdbc-backend).

### `encryption.backend`

Default: `com.google.cloud.broker.encryption.backends.CloudKMSBackend`

[Encryption](encryption.md) backend class.

### `encryption.cloud-kms.kek-uri`

URI of the Cloud KMS key encryption key (KEK) used to [encrypt/decrypt](encryption.md) the data encryption key (DEK).

### `encryption.cloud-kms.dek-uri`

URI in Cloud Storage for the data encryption key (DEK) used to [encrypt/decrypt](encryption.md) data.

### `logging-level`

Default: `INFO`

Base level for application logs.

### `oauth.client-secret-json-path`

Path to the OAuth client secret JSON file used by the [Authorizer](authorizer.md) app and the [refresh token provider](providers.md#refresh-token-provider)
to generate and use refresh tokens.

### `providers.access-tokens.local-cache-time`

Default: `30` (in seconds)

[Local cache](caching.md#local-cache) lifetime for access tokens.

### `providers.access-tokens.remote-cache-time`

Default: `60` (in seconds)

[Remote cache](caching.md#remote-cache) lifetime for access tokens.

### `provider.backend`

Default: `com.google.cloud.broker.apps.brokerserver.accesstokens.providers.RefreshTokenProvider`

Access token [provider](providers.md) backend class.

### `provider.shadow-service-accounts.project`

GCP project where the [shadow service accounts](providers.md#service-account-provider) are hosted.

### `provider.shadow-service-accounts.username-pattern`

Default: `%s-shadow`

Pattern for the [shadow service account](providers.md#service-account-provider) username.

### `proxy-users`

Default: `[]` (Empty string)

Whitelist of [proxy users](authentication.md#proxy-user-impersonation).

### `remote-cache.backend`

Default: `com.google.cloud.broker.caching.remote.RedisCache`

[Remote cache](caching.md#remote-cache) backend class.

### `remote-cache.redis.db`

Default: `0`

Redis database number used for caching. Only necessary if you use the [Redis cache backend](caching.md#redis-backend).

### `remote-cache.redis.host`

Default: `localhost`

Host of the Redis cache server. Only necessary if you use the [Redis cache backend](caching.md#redis-backend).

### `remote-cache.redis.port`

Default: `6379`

Port of the Redis cache server. Only necessary if you use the [Redis cache backend](caching.md#redis-backend).

### `scopes.whitelist`

Default: `["https://www.googleapis.com/auth/devstorage.read_write"]`

Whitelist of API scopes for access tokens.

### `server.host`

Default: `0.0.0.0`

Hostname where the broker application is served.

### `server.port`

Default: `5000`

Port number where the broker application is served. A valid port value is between `0` and `65535`.
A port number of `0` will let the system pick up an ephemeral port in a bind operation.

### `server.tls.certificate-path`

Path on the filesystem for the [TLS](tls.md) certificate.

### `server.tls.enabled`

Default: `true`

Flag to enable/disable [TLS](tls.md).

### `server.tls.private-key-path`

Path on the filesystem for the [TLS](tls.md) private key.

### `sessions.local-cache-time`

Default: `30` (in seconds)

[Local cache](caching.md#local-cache) lifetime for [session](sessions.md) details.

### `sessions.maximum-lifetime`

Default: `604800000` (7 days, in milliseconds)

[Session](sessions.md) maximum lifetime.

### `sessions.renew-period`

Default: `86400000` (24 hours, in milliseconds)

[Session](sessions.md) lifetime increment.
