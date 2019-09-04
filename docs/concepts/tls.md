## TLS

Communication between the [broker connector](connector.md) and the [broker server](broker-server.md) can be
encrypted with [TLS](https://en.wikipedia.org/wiki/Transport_Layer_Security).

TLS encryption is **highly recommended** for production environments.

To enable TLS:

1. Create a domain for the broker service.
2. Get a certificate and private key from a [certificate authority](https://en.wikipedia.org/wiki/Certificate_authority).
3. Set the [`TLS_ENABLED`](settings.md#TLS_ENABLED) setting to `true` and provide the certificate and private key with the
   [`TLS_CRT_PATH`](settings.md#TLS_CRT_PATH) and [`TLS_KEY_PATH`](settings.md#TLS_KEY_PATH) setting.
4. Configure the broker connector's [properties](connector.md#configuration-properties) to use TLS.