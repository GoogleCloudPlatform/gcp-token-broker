# Sessions

The broker session system is what enables [delegated authentication](authentication.md#delegated-authentication).
This system dispenses tokens for short-lived sessions that can then be traded for GCP access tokens without having
to provide the user's credentials every in every request.

This system is inspired by, and is fully compatible with, Hadoop's delegation token facility. Essentially, a broker
session token corresponds to a Hadoop-style delegation token that can be used to access the broker service in
distributed jobs.

## Session lifecycle

The are 4 keys stages in the lifecycle of a session token: session creation, access token trade, session renewal,
and session cancellation.

<img src="../img/delegated-auth-architecture.svg">

### Session creation

A session is created when a client calls the `GetSessionToken` broker endpoint right before submitting a new distributed
job.

The `GetSessionToken` endpoint requires [direct authentication](authentication.md#direct-authentication) to ensure that
the caller is correctly authenticated with the user's credentials.

For each new session, the broker adds a new record in the [database](database.md) with some details about the session:

-   `id`: Automatically generated unique ID for the session.
-   `creation_time`: Time at which the session was created, that is, just before the job started.
-   `expires_at`: Time at which the session will expire. If the Yarn Resource Manager renews
    the session token, then this time will be extended to some time in the future (24 hours later, by default).
-   `owner`: Name of the original Kerberos principal who submitted the job.
-   `renewer`: Name of the Kerberos principal who is authorized to renew and cancel the token.
-   `scope`: Google API scope used to generate access tokens.
-   `target`: Name of the GCP resource (e.g. a GCS bucket) associated with the session.

The broker then generates a signed token for the session (i.e the "session token").

### Access token trade

After it obtains a new session token, the client submits the job and passes the token to the distributed job's tasks.
When a task needs to access a GCP resource (e.g. a GCS bucket), the task calls the `GetAccessToken` broker endpoint
and submits the session token.

The broker then verifies the session token's signature, generates a new GCP access token, and returns the access
token to the caller.

In other words, the `GetAccessToken` endpoint trades a session token for a GCP access token.

### Session renewal

When a session is about to expire during the execution of a job, the session token's renewer calls the
`RenewSessionToken` broker endpoint.

The `RenewSessionToken` endpoint requires [direct authentication](authentication.md#direct-authentication) to ensure
that the caller is correctly authenticated with the renewer's credentials.

If authentication is successful, then the session's `expires_at` value in the session's [database](database.md) record
extended to [`sessions.renew-period`](settings.md#sessionsrenew-period) millisesonds in the future from now.

A session can be renewed (i.e. have its lifetime extended) any number of times, until the session's lifetime reaches the
[`sessions.maximum-lifetime`](settings.md#sessionsmaximum-lifetime) value, at which point the token becomes obsolete and
inoperable.

### Session cancellation

When a distributed job is completed, the session token's renewer calls the `CancelSessionToken` broker endpoint.

The `CancelSessionToken` endpoint requires [direct authentication](authentication.md#direct-authentication) to ensure
that the caller is correctly authenticated with the renewer's credentials.

If authentication is successful, then the session is deleted from the broker's [database](database.md), at which point
the session token becomes obsolete and inoperable.

### Purging expired sessions

If a session isn't explicitly cancelled (for example if a Hadoop crashed before completion), a stale record may remain
in the database after the session expires. To purge such expired records, you can run the following command (for example
as part of a regular cron job):

```shell
CONFIG_FILE=/<path>/application.conf java com.google.cloud.broker.apps.brokerserver.sessions.SessionCleanup
```
