# Broker server

The broker server is the core component of the broker architecture. It accepts [gRPC](https://grpc.io/) requests from
clients and serves four different endpoints:

-   `GetAccessToken`: Returns a GCP access token. Can be called either using [direct authentication](authentication.md#direct-authentication),
    [delegated authentication](authentication.md#delegated-authentication), or [proxy user impersonation](authentication.md#proxy-user-impersonation).
-   `GetSessionToken`: Called by a user client to create a new [session](sessions.md).
    Requires [direct authentication](authentication.md#direct-authentication) or [proxy user impersonation](authentication.md#proxy-user-impersonation).
-   `RenewSessionToken`: Called by a [session](sessions.md) renewer to extend the lifetime of a session during the
    execution of a job. Requires [direct authentication](authentication.md#direct-authentication).
-   `CancelSessionToken`: Called by a [session](sessions.md) renewer to terminate a session at the end of a job.
    Requires [direct authentication](authentication.md#direct-authentication).

## Running the server

To run the server:

1.  Retrieve the JAR file for the broker server package from [Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:broker):
    ```xml
    <groupId>com.google.cloud.broker</groupId>
    <artifactId>broker-server</artifactId>
    ```
2.  Retrieve all the JAR files from Maven Central for the broker extensions (i.e. the [encryption backend](encryption.md#encryption-backends),
    [database backend](database.md#database-backends), [remote cache backends](caching.md#remote-cache-backends))
    that you wish to use for your deployment.
3.  Place all the JAR files in the `CLASSPATH`.
4.  Create an `application.conf` file with the [settings](settings.md) for your environment.
5.  [Initialize the database](database.md#database-initialization).
6.  Run the following command:
    ```shell
    CONFIG_FILE=/<path>/application.conf java com.google.cloud.broker.apps.brokerserver.BrokerServer
    ```
