# Broker connector

The broker connector is an extension for the [GCS Connector](https://github.com/GoogleCloudPlatform/bigdata-interop/tree/master/gcs),
which acts as an interface between Hadoop and [Cloud Storage](https://cloud.google.com/storage/).

The broker connector is used by the GCS Connector to access the broker service's gRPC endpoints. Recent minor versions
of both Hadoop 2 & 3 are supported.

You can find the broker connector's [package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:broker-connector):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>broker-connector</artifactId>
```

The broker connector can be downloaded as a single JAR file, which must be placed in the `CLASSPATH` of different
environments:

-   On the user's client node, so the client can access the `GetAccessToken` and `GetSessionToken` endpoints.
-   On the master node(s) where the session token renewer (e.g. Yarn) is running, so the renewer can
    call the `RenewSessionToken` and `CancelSessionToken` endpoints.
-   On the worker nodes, so the distributed tasks can call the `GetAccessToken` to trade a session token for
    an access token.

For more information, see the documentation about [authentication](authentication.md) and [sessions](sessions.md).

## Configuration properties

This section contains all the available Hadoop configuration properties for the broker connector.

### `gcp.token.broker.kerberos.principal`

Full name for the broker's Kerberos service principal.

### `gcp.token.broker.uri`

URI for the broker server. If `https` is specified, then you must also provide either `gcp.token.broker.tls.certificate`
or `gcp.token.broker.tls.certificate.path`.

### `gcp.token.broker.tls.certificate`

Alternative to `gcp.token.broker.tls.certificate.path`. Contents of the TLS certificate for the broker service.
Used only if `gcp.token.broker.tls.enabled` is `true`.

### `gcp.token.broker.tls.certificate.path`

Alternative to `gcp.token.broker.tls.certificate`. File path of TLS certificate for the broker service.
Used only if `gcp.token.broker.tls.enabled` is `true` and `gcp.token.broker.tls.certificate`  is not provided.

## Configuration checks

To verify that your client environment is correctly configured, you can run this simple command:

```shell
java -cp $(hadoop classpath) com.google.cloud.broker.client.hadoop.fs.CheckConfig
```
