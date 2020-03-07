# Encryption

The broker encrypts different types of information:

-   Access tokens before they are stored in the [remote cache](caching.md#remote-cache).
-   Refresh tokens used by the [refresh token provider](providers.md#refresh-token-provider), if that is the provider
    that you elect to use.

## Encryption backends

To select a database backend, set the [`encryption.backend`](settings.md#encryptionbackend) setting
to the backend's class path.

Below is the list of available database backends:

### Cloud KMS

_Class path:_ `com.google.cloud.broker.encryption.backends.CloudKMSBackend`

The Cloud KMS backend uses [envelope encryption](https://cloud.google.com/kms/docs/envelope-encryption)
to encrypt and decrypt data. It uses a [Cloud KMS](https://cloud.google.com/kms/) key encryption key (KEK)
to wrap an AES256 data encryption key (DEK) stored in Cloud Storage or on the local filesystem.

To generate the data encryption key and store it in Cloud Storage, run the
`GenerateDEK` command:

```shell
export BROKER_VERSION=$(cat VERSION)
java -cp encryption-backend-cloud-kms-${BROKER_VERSION}-jar-with-dependencies.jar:code/core/target/broker-core-${BROKER_VERSION}-jar-with-dependencies.jar \
  com.google.cloud.broker.encryption.GenerateDEK [dekUri] [kekUri]
```

*   In the above command, replace `[dekUri]` with the URI of the DEK (e.g. `file:///path/to/dek.json` you want to store
    the DEK on the local system, or `gs://YOUR_BUCKET/dek.json` if you want to store the DEK on Cloud Storage).
*   Replace `[kekUri]` with the URI of the KEK: `projects/[PROJECT]/locations/[REGION]/keyRings/[KEY_RING]/cryptoKeys/[KEY_NAME]`
    (Replace the `[PROJECT]`, `[REGION]`, `[KEY_RING]`, and `[KEY_NAME]` with the appropriate values).
*   You can omit the `dekUri` and `kekUri` command parameters if you provide a [settings](settings.md) file with the
    [`encryption.cloud-kms.dek-uri`](settings.md#encryptioncloud-kmsdek-uri) and
    [`encryption.cloud-kms.kek-uri`](settings.md#encryptioncloud-kmskek-uri) settings.

This backend is available as a [separate package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:encryption-backend-cloud-kms):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>encryption-backend-cloud-kms</artifactId>
```

This backend requires that you set the following setting(s): [`encryption.cloud-kms.kek-uri`](settings.md#encryptioncloud-kmskek-uri),
[`encryption.cloud-kms.dek-uri`](settings.md#encryptioncloud-kmsdek-uri).

### Dummy encryption backend

_Class path:_ `com.google.cloud.broker.encryption.backends.DummyEncryptionBackend`

The Dummy encryption backend doesn't actually encrypt anything. It is only useful for testing
and development purposes. Do _not_ use it in production.

This backend is included in the [broker server](broker-server.md) package.
