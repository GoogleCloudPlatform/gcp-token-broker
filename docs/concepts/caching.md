# Caching

The broker application caches access tokens to account for the case where hundreds or thousands
of tasks might request an access token for the same user at the same time, for example at the beginning
of a large Map/Reduce job. This way, all tasks accessing the broker within the cache lifetime window
will share the same token. This allows to increase performance and to reduce load on the Google token API.

The broker application uses two types of caches: remote and local.

## Remote vs local caching

### Remote cache

When a new access token is generated, the token is [encrypted](encryption.md) and then stored in a
remote cache for a short period of time, controlled by the [`provider.access-tokens.remote-cache-time`](settings.md#provideraccess-tokensremote-cache-time)
setting.

You can elect to use one of the available [remote cache backends](#remote-cache-backends).

### Local cache

When a broker JVM obtains an access token for a user (either after generating it or pulling it from
the remote cache), it caches the token unencrypted in its local memory for a short period of time,
controlled by the [`provider.access-tokens.local-cache-time`](settings.md#provideraccess-tokenslocal-cache-time) setting.

## Remote cache backends

To select a remote cache backend, set the [`remote-cache.backend`](settings.md#remote-cachebackend) setting
to the backend's class path.

Below is the list of available cache backends:

### Redis backend

_Class path:_ `com.google.cloud.broker.caching.remote.RedisCache`

You can run [Redis](https://redis.io/) either as a self-managed service on Computer Engine, or as a fully-managed
service on [Cloud Memorystore](https://cloud.google.com/memorystore/docs/redis/).

The Redis backend is available as a [separate package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:cache-backend-redis):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>cache-backend-redis</artifactId>
```

This backend requires that you set the following setting(s): [`remote-cache.redis.host`](settings.md#remote-cacheredishost),
[`remote-cache.redis.port`](settings.md#remote-cacheredisport), and
[`remote-cache.redis.db`](settings.md#remote-cacheredisdb).

### Dummy backend

_Class path:_ `com.google.cloud.broker.caching.remote.DummyCache`

The Dummy cache backend doesn't actually cache anything. It is useful only for testing and development purposes.
Do not use this dummy backend in production, as your broker service would likely not be able to handle production-scale
loads without a real cache backend.

This backend is included in the [broker server](broker-server.md) package.