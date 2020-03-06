# Settings

Settings for the Authorizer app and broker service use the [HOCON](https://en.wikipedia.org/wiki/HOCON) format developed
by [Lightbend](https://en.wikipedia.org/wiki/Lightbend) (See [official documentation](https://github.com/lightbend/config).

To customize settings for your environment, create an `application.conf` file and pass it to your application using one
one the following ways:

*   `-Dconfig.file` property set to the configuration file's path on the filesystem.
*   `CONFIG_FILE` environment variable set to the configuration file's path on the filesystem.
*   `CONFIG_BASE64` environment variable set to the base64-encoded contents of the configuration file.
*   `CONFIG_GCS` environment set to the URI of the configuration file in a Cloud Storage bucket.

Below is the list of available settings, in alphabetical order.

## Available settings

### `authentication.backend`

Default: `com.google.cloud.broker.authentication.backends.SpnegoAuthenticator`

[Authentication](authentication.md) backend class.

### `authentication.spnego.keytabs`

List of principal/keytab pairs for the broker service to log in with. For example:

```
[{principal=broker/example.com@MYREALM, keytab=/etc/security/broker.keytab}, {principal=broker/foo@BAR, keytab=/etc/security/broker-foobar.keytab}]
```

### `authorizer.host`

Host for the [Authorizer app](authorizer.md)'s server.

### `authorizer.port`

Port for the [Authorizer app](authorizer.md)'s server.

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
The KEK URI must use the following format: `projects/[PROJECT]/locations/[REGION]/keyRings/[KEY_RING]/cryptoKeys/[KEY_NAME]`
(Replace the `[PROJECT]`, `[REGION]`, `[KEY_RING]`, and `[KEY_NAME]` with the appropriate values).

### `encryption.cloud-kms.dek-uri`

URI in Cloud Storage or on the local filesystem for the data encryption key (DEK) used to
[encrypt/decrypt](encryption.md) data. The DEK URI must have the `gs://` prefix if the key is stored in Cloud Storage
or the `file://` prefix if the key is stored on the local filesystem.

### `gcp-project`

Project ID for the broker service.

### `gsuite-admin`

Name of an admin user for your GSuite domain. Required if using `groups` attribute in the [`proxy-users`](#proxy-users)
setting for [proxy user impersonation](authentication.md#proxy-user-impersonation).

### `logging.level`

Default: `INFO`

Base level for application logs.

### `oauth.client-id`

Oauth client ID used by the [Authorizer](authorizer.md) app and the [refresh token provider](providers.md#refresh-token-provider)
to generate and use refresh tokens.

If used, you must also provide `oauth.client-secret`.

Alternatively you can use `oauth.client-secret-json-path`.

### `oauth.client-secret`

Oauth client secret used by the [Authorizer](authorizer.md) app and the [refresh token provider](providers.md#refresh-token-provider)
to generate and use refresh tokens.

If used, you must also provide `oauth.client-id`.

Alternatively you can use `oauth.client-secret-json-path`.

### `oauth.client-secret-json-path`

Alternative to `oauth.client-id` and `oauth.client-secret`.

Path to the OAuth client secret JSON file used by the [Authorizer](authorizer.md) app and the [refresh token provider](providers.md#refresh-token-provider)
to generate and use refresh tokens.

### `provider.access-tokens.local-cache-time`

Default: `30` (in seconds)

[Local cache](caching.md#local-cache) lifetime for access tokens.

### `provider.access-tokens.remote-cache-time`

Default: `60` (in seconds)

[Remote cache](caching.md#remote-cache) lifetime for access tokens.

### `provider.backend`

Default: `com.google.cloud.broker.apps.brokerserver.accesstokens.providers.HybridProvider`

Access token [provider](providers.md) backend class.

### `provider.hybrid.user-provider`

Default: `com.google.cloud.broker.apps.brokerserver.accesstokens.providers.RefreshTokenProvider`

The sub-provider used by the [hybrid provider](providers.md#hybrid-provider) for Google users.

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

### `secret-manager.downloads`

List of [secrets](secret-management.md) to download from Secret Manager.

### `server.host`

Default: `0.0.0.0`

Hostname where the broker application is served.

### `server.port`

Default: `8080`

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

### `user-mapping.mapper`

Default: `com.google.cloud.broker.usermapping.KerberosUserMapper`

[User mapping](user-mapping.md) backend class.

### `user-mapping.rules`

List of user mapping rules required by the [Kerberos user mapper](user-mapping.md#kerberos-user-mapper).
