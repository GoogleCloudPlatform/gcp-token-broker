# Secret management

The broker service needs to access some sensitive pieces of information to run its operations. Such pieces include:

*   Keytab file for Kerberos [authentication](authentication.md).
*   OAuth client secret for retrieving [refresh tokens](providers.md#refresh-token-provider).
*   Data encryption key (DEK) for envelope [encryption](encryption.md).
*   [TLS](tls.md) certificate and private key.

It's a good idea to keep master copies of this information in a centralized, secure location. One great place for this
is [Secret Manager](https://cloud.google.com/secret-manager), a fully-managed Google Cloud service to encrypt, store,
manage, and audit infrastructure and application-level secrets.

You can create a secret in Secret Manager using the `gcloud` command, for example:

```shell
gcloud secrets create keytab --replication-policy="automatic"
gcloud secrets versions add keytab --data-file=broker.keytab
```

## Downloading secrets

The broker and authorizer applications have the ability to download secrets hosted in Secret Manager and then cache them
as files on the local filesystem. Downloading secrets happens automatically at startup time. You can also trigger the
download by running the `com.google.cloud.broker.secretmanager.DownloadSecrets` command.

To specify which secrets to download and where to store them on the filesystem, use the `secret-manager.downloads`
setting. Take the following example (Replace `[PROJECT_ID]` with your project ID):

```
secret-manager {
  downloads = [
    { secret = "projects/[PROJECT_ID]/secrets/keytab/versions/latest", file = "/secrets/keytab" },
    { secret = "projects/[PROJECT_ID]/secrets/dek/versions/latest", file = "/secrets/dek.json" }
  ]
}
```

The above example lets the application download two secrets (`keytab` and `dek`) at startup time and then cache them as
two separate files (`/secrets/keytab` and `/secrets/dek.json`) on the local filesystem.

## Required vs optional secrets

By default all specified secrets are marked as `required`. This means that, if the download fails –either because the
secret doesn't exist or the broker service account does not have permission to access the secret–, then the application
will also fail to start.

You can mark a secret as optional by setting the `required=false` attribute, for example:

```
secret-manager {
  downloads = [
    { secret = "projects/[PROJECT_ID]/secrets/keytab/versions/latest", file = "/secrets/keytab", required=false }
  ]
}
```

If a secret is marked as optional, and if the secret download fails, then the application will be allowed to start up
and only emit a warning in the logs. If the secret becomes available at a later time, then you must either restart the
application –so it can re-attempt to download the secret–, or run the `com.google.cloud.broker.secretmanager.DownloadSecrets`
command.