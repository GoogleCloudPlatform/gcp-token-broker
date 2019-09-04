# Caching

The broker application caches access tokens to account for the case where hundreds or thousands
of tasks might request an access token for the same user at the same time, for example at the beginning
of a large Map/Reduce job. This way, all tasks accessing the broker within the cache lifetime window
will share the same token. This allows to increase performance and to reduce load on the Google token API.

The broker application uses two types of caches: remote and local.

## Remote vs local caching

### Remote cache

When a new access token is generated, the token is [encrypted](encryption.md) and then stored in a
remote cache for a short period of time, controlled by the [`ACCESS_TOKEN_REMOTE_CACHE_TIME`](settings.md#ACCESS_TOKEN_REMOTE_CACHE_TIME)
setting. You can elect to use one of the available [remote cache backends](#remote-cache-backends).

### Local cache

When a broker JVM obtains an access token for a user (either after generating it or pulling it from
the remote cache), it caches the token unencrypted in its local memory for a short period of time,
controlled by the [`ACCESS_TOKEN_LOCAL_CACHE_TIME`](settings.md#ACCESS_TOKEN_LOCAL_CACHE_TIME) setting.

## Remote cache backends

To select a remote cache backend, set the [`REMOTE_CACHE`](settings.md#REMOTE_CACHE) setting
to the backend's class path.

Below is the list of available cache backends:

### Redis backend

Class path: `com.google.cloud.broker.caching.remote.RedisCache`

You can run [Redis](https://redis.io/) either as a self-managed service on Computer Engine, or as a fully-managed
service on [Cloud Memorystore](https://cloud.google.com/memorystore/docs/redis/).

The Redis backend is available as a separate [package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:cache-backend-redis):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>cache-backend-redis</artifactId>
```

This backend requires that you set the following settings: [`REDIS_CACHE_HOST`](settings.md#REDIS_CACHE_HOST),
[`REDIS_CACHE_PORT`](settings.md#REDIS_CACHE_PORT), and [`REDIS_CACHE_DB`](settings.md#REDIS_CACHE_DB).

### Dummy backend

Class path: `com.google.cloud.broker.caching.remote.DummyCache`

The Dummy cache backend doesn't actually cache anything. It is useful for testing and development purposes.
Do not use it in production, as your broker service would likely not scale without a real cache backend.

This backend is included in the [broker server](broker-server.md) package.