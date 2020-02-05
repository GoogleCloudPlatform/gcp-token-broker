# Database

The broker uses a database to store different types of information:

- [Session](sessions.md) details to enable [delegated authentication](#delegated-authentication).
- Refresh tokens used by the [refresh token provider](providers.md#refresh-token-provider), if that is the provider that
  you elect to use.

## Database backends

To select a database backend, set the [`database.backend`](settings.md#databasebackend) setting
to the backend's class path.

Below is the list of available database backends:

### Cloud Datastore backend

_Class path:_ `com.google.cloud.broker.database.backends.CloudDatastoreBackend`

The Cloud Datastore backend connects to a database hosted on [Cloud Datastore](https://cloud.google.com/datastore),
a highly scalable and fully-managed NoSQL database in GCP.

This backend is available as a [separate package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:database-backend-cloud-datastore):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>database-backend-cloud-datastore</artifactId>
```

This backend requires that you set the following setting(s): [`gcp-project`](settings.md#gcp-project).

This backend doesn't require initializing the database, as the tables (i.e. "[kinds](https://cloud.google.com/datastore/docs/concepts/entities#kinds_and_identifiers)")
will automatically be created when the first records are inserted.

### JDBC backend

_Class path:_ `com.google.cloud.broker.database.backends.JDBCBackend`

The JDBC backend connects to a relational database. Currently Postgres, MySQL, MariaDB, and SQLite are supported.

This is available as a [separate package on Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker%20AND%20a:database-backend-jdbc):

```xml
<groupId>com.google.cloud.broker</groupId>
<artifactId>database-backend-jdbc</artifactId>
```

To use one of the supported RDBMS, you also have to include the corresponding driver in your classpath. Here is a
non-exhaustive list of drivers you can use:

- For Postgres: `org.postgresql:postgresql`.
- For MariaDB and MySQL: `org.mariadb.jdbc:mariadb-java-client`.
- For SQLite: `org.xerial:sqlite-jdbc`.

This backend requires that you set the following setting(s): [`database.jdbc.driver-url`](settings.md#databasejdbcdriver-url).

### Dummy database backend

_Class path:_ `com.google.cloud.broker.database.backends.DummyDatabaseBackend`

The dummy database backend stores data in the JVM local memory. It only makes sense to use it if there is
a single running JVM.

This backend is only useful for testing and development purposes. Do *not* use it in production.

This backend is included in the [broker server](broker-server.md) package.

## Database initialization

Some database backends require that you initialize the database to create the tables.

Follow these steps to initialize the database:

1. Retrieve the JAR file for the [broker server](broker-server.md) package.
2. Retrieve the JAR file from [Maven Central](https://search.maven.org/search?q=g:com.google.cloud.broker) for the database backend of your choice.
3. Place the JAR files in the `CLASSPATH`.
4. Create an `application.conf` file with the [settings](settings.md) for your environment.
5. Run the following command:

   ```shell
   java -Dconfig.file=/<path>/application.conf com.google.cloud.broker.database.InitializeDatabase
   ```