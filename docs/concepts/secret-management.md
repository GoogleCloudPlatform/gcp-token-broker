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

The broker application has the ability to download secrets hosted in Secret Manager at startup time and then cache them
as files on the local filesystem. For that, you must provide the `secret-manager.downloads` setting. Take the following
example (Replace `[PROJECT_ID]` with your project ID):

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
