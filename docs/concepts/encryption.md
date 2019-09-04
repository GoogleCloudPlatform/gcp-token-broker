# Encryption

The broker encrypts different types of information:

- [Session](sessions.md) passwords embedded in session tokens.
- Access tokens before they are stored in the [remote cache](caching.md#remote-cache).
- Refresh tokens used by the [refresh token provider](providers.md#refresh-token-provider), if that is the provider that you elect
  to use.

## Encryption backends

To select a database backend, set the [`ENCRYPTION_BACKEND`](settings.md#ENCRYPTION_BACKEND) setting
to the backend's class path.

Below is the list of available database backends:

### Cloud KMS

Class path: `com.google.cloud.broker.encryption.backends.CloudKMSBackend`

The Cloud KMS backend uses [Cloud KMS](https://cloud.google.com/kms/) to encrypt and decrypt data.

This backend is available as a separate [package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:encryption-backend-cloud-kms):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>encryption-backend-cloud-kms</artifactId>
```

This backend requires that you set the following setting: [`GCP_PROJECT`](settings.md#GCP_PROJECT),
[`ENCRYPTION_KEY`](settings.md#ENCRYPTION_KEY), [`ENCRYPTION_KEY_RING`](settings.md#ENCRYPTION_KEY_RING),
and [`ENCRYPTION_KEY_RING_REGION`](settings.md#ENCRYPTION_KEY_RING_REGION).

### Dummy encryption backend

Class path: `com.google.cloud.broker.encryption.backends.DummyEncryptionBackend`

The Dummy encryption backend doesn't actually encrypt anything. It is only useful for testing
and development purposes. Do not use it in production.

This backend is included in the [broker server](broker-server.md) package.