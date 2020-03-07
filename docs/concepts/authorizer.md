# Authorizer

The Authorizer is a Web UI that walks users through an OAuth 2.0 flow to collect OAuth refresh tokens.

It is necessary for your users to use the Authorizer if you choose to use the [`RefreshTokenProvider`](providers.md#refresh-token-provider).

When users complete the OAuth flow, the Authorizer obtains a refresh token, [encrypts](encryption.md) it,
and stores it in the [database](database.md). At that point the user is fully authorized to use the broker service.
It is only necessary for each user to go through the flow once.

The Authorizer application serves three endpoints:

-   `/`: Landing page for the Authorizer app.
-   `/google/login`: Redirects the user to the Google login page.
-   `/google/oauth2callback`: Receives the OAuth 2.0 authorization code from Google, then uses it to
    obtain a refresh token for the authenticated Google user. The refresh token is then encrypted
    and stored in the database.

## Running the Authorizer

To run the Authorizer:

1.  Retrieve the JAR file for the Authorizer package from [Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:authorizer):
    ```xml
    <groupId>com.google.cloud.broker</groupId>
    <artifactId>authorizer</artifactId>
    ```
2.  Retrieve all the JAR files from Maven Central for the [encryption backend](encryption.md#encryption-backends) and
    [database backend](database.md#database-backends)) that you wish to use for your deployment.
3.  Place all the JAR files in the `CLASSPATH`.
4.  Create an `application.conf` file with the [settings](settings.md) for your environment.
5.  [Initialize the database](database.md#database-initialization).
6.  Run the following command:

    ```shell
    CONFIG_FILE=/<path>/application.conf java com.google.cloud.broker.apps.authorizer.Authorizer
    ```
