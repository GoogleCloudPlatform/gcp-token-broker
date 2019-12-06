# Access token providers

The broker service can use three different providers to generate access tokens for Google APIs:

- Refresh tokens provider (`RefreshTokenProvider`)
- Shadow service accounts provider (`ShadowServiceAccountProvider`)
- Domain-wide delegation authority provider (`DomainWideDelegationAuthorityProvider`)

You can specify which provider you'd like to use by setting the [`PROVIDER`](settings.md#PROVIDER) setting to the provider's class path.

All providers are included in the [broker server](broker-server.md) package.

## Refresh token provider

Class path: `com.google.cloud.broker.apps.brokerserver.accesstokens.providers.RefreshTokenProvider`

A refresh token is used in OAuth flows to generate new access tokens for Google identities.

Refresh tokens are obtained by the [Authorizer app](authorizer.md), then stored in the broker's [database](database.md).
**Refresh tokens are long-lived credentials, so they must be stored securely.**

Below is the schema for the refresh token table:

- `id`: Name of the Cloud Identity associated with the token.
- `creation_time`: Time at which the refresh token was obtained by the broker.
- `value`: [Encrypted](encryption.md) value of the OAuth refresh token.

Obtaining a refresh token is a one-time process for each user, although there are some cases where a refresh token can
expire (for more details, see [refresh token expiration](https://developers.google.com/identity/protocols/OAuth2#expiration)).

Once the broker has a refresh token for a user, it can use the `RefreshTokenProvider` to generate access tokens for that user
on demand as requested by the broker clients.

This provider offers tight scoping on a per-user basis. This allows to ensure that the broker may only generate access tokens
for a specific whitelist of Google users.

Let's take an example:

- The user Alice belongs to Group A, which owns Bucket A. The user Bob doesn't belong to any group and doesn't have access to any buckets.
- You create a service account for the broker service and enable domain-wide delegation authority for that service account.
- Alice and Bob use the Authorizer app to let the broker obtain refresh tokens
- The broker's service account can now use the refresh tokens to obtain access tokens for both Bob and Alice. However, only an access token obtained for Alice (or any other user that also belongs to Group A) can be used to access Bucket A.

<img src="../img/access-example-users.svg">

This provider requires that you set the following settings: [`DOMAIN_NAME`](settings.md#DOMAIN_NAME), and
[`OAUTH_CLIENT_SECRET_JSON_PATH`](settings.md#OAUTH_CLIENT_SECRET_JSON_PATH).

## Service account provider

Class path: `com.google.cloud.broker.apps.brokerserver.accesstokens.providers.ShadowServiceAccountProvider`

The shadow service accounts approach leverages a Cloud IAM feature called [short-lived service account credentials](https://cloud.google.com/iam/docs/creating-short-lived-service-account-credentials).
This feature allows a given service account to obtain access tokens on behalf of other service accounts.

This approach only works with service accounts, not users. This means that you need to create separate service accounts, one for
each human user. Those are called **shadow service accounts**.

Let's take an example:

- The user Alice belongs to Group A, which owns Bucket A. The user Bob doesn't belong to any group and doesn't have access to any buckets.
- You create a service account for Bob. You create another service account for Alice, and add Alice's service account to Group A.
- You create a service account for the broker service and give it the [Service Account Token Creator](https://cloud.google.com/iam/docs/service-accounts#the_service_account_token_creator_role)
  role for Bob's and Alice's service accounts.
- The broker's service account can now obtain access tokens for both Bob's and Alice's service accounts. However, only an access
  token obtained for Alice's service account can be used to access Bucket A.

<img src="../img/access-example-service-accounts.svg">

To use the `ShadowServiceAccountProvider`, follow this procedure:

- Create a separate shadow service account for each user that is expected to use Hadoop in GCP.
- Create a service account for the broker.
- Give the broker's service account the [Service Account Token Creator](https://cloud.google.com/iam/docs/service-accounts#the_service_account_token_creator_role)
  role for all shadow service accounts that are expected to use the Hadoop platform (e.g. if creating a broker for Hive,
  then only give that role for the Hive users' shadow service accounts). This essentially controls what users can be impersonated.

This provider requires that you set the following settings: [`SHADOW_PROJECT`](settings.md#SHADOW_PROJECT),
[`SHADOW_USERNAME_PATTERN`](settings.md#SHADOW_USERNAME_PATTERN), and [`JWT_LIFE`](settings.md#JWT_LIFE).

## Domain-wide delegation authority provider

Class path: `com.google.cloud.broker.apps.brokerserver.accesstokens.providers.DomainWideDelegationAuthorityProvider`

In GSuite domains, the domain administrator can grant third-party applications with domain-wide access to its users' data — this is
referred as [domain-wide delegation of authority](https://developers.google.com/admin-sdk/directory/v1/guides/delegation). To delegate
authority this way, domain administrators can use service accounts with OAuth 2.0.

One or more API scopes can be set to restrict what your application can access. For example `https://www.googleapis.com/auth/devstorage.read_write` for read/write access
to Cloud Storage.

**Warning**: Do _not_ use this provider in production unless you know what you are doing.
A service account blessed with domain-wide delegation authority can impersonate any users across the entire organization,
including users that are not at all involved in your specific use case. For example: if the [broker server](broker-server.md) or a
[proxy user](authentication.md#proxy-user-impersonation) is compromised, then the attacker would be able to impersonate,
for example, your company’s CEO and access files (e.g. your company's financials documents) that the CEO may have stored on GCS.

To use the `DomainWideDelegationAuthorityProvider`, first create a service account in GCP and enable domain-wide delegation:

<img src="../img/dwd-service-accounts-screen.png">

Then in the Google Admin console, add the service account ID and set the required API scopes:

<img src="../img/dwd-admin-screen.png">

At that point, the service account is allowed to impersonate any user in the organization's domain and obtain access tokens for those users, although only for the specified API scopes.

This provider requires that you set the following settings: [`DOMAIN_NAME`](settings.md#DOMAIN_NAME) and [`JWT_LIFE`](settings.md#JWT_LIFE).
