# TLS

Communication between the [broker connector](connector.md) and the [broker server](broker-server.md) can be
encrypted with [TLS](https://en.wikipedia.org/wiki/Transport_Layer_Security).

TLS encryption is **highly recommended** for production environments.

To enable TLS:

1.  Create a domain for the broker service.
2.  Get a certificate and private key from a [certificate authority](https://en.wikipedia.org/wiki/Certificate_authority).
3.  Set the [`server.tls.enabled`](settings.md#servertlsenabled) setting to `true` and provide the certificate and
    private key with the [`server.tls.certificate-path`](settings.md#servertlscertificate-path) and
    [`server.tls.private-key-path`](settings.md#servertlsprivate-key-path) setting.
4.  Configure the broker connector's [properties](connector.md#configuration-properties) to use TLS.
