## Tutorials

This section provides a few simple test scenarios that you can run on the test Dataproc cluster.
To SSH in the Dataproc cluster's master node, run this command:

```shell
gcloud compute ssh test-cluster-m
```

Once you're SSH'ed in, log in as one of your test users with Kerberos, for example:

```shell
kinit alice@$REALM
```

**Note:** For the sake of simplicity for the demo, the Kerberos passwords for the test users were
hardcoded in the demo's deployment (See the details in the [startup script template](../../terraform/startup-script-kdc.tpl)
and the `kadmin.local` commands in the origin KDC's [terraform specification file](../../terraform/origin_kdc.tf).
Those hardcoded passwords are the same as the usernames (e.g. the password for "alice@$REALM" is "alice").
Those hardcoded passwords are *not* the passwords that you would have set for the GSuite users in the
[Prerequisites](../deploy/index.md#prerequisites) section, as that would be the case in a production environment where
the KDC's database would be synced with your LDAP database.

Once the Kerberos user is logged-in, you are ready to run the commands in the test scenarios
described the following sub-sections. After each command, you can verify in the GCS audit logs
that the demo GCS bucket is in fact accessed by the expected GSuite user, that is
"alice@your-domain.com" (See the [Logging](../concepts/logging.md) section to learn how to view the logs).

### Configuration checks

Run this command to verify that your client environment is correctly configured to connect to the server:

```shell
java -cp $(hadoop classpath) com.google.cloud.broker.client.hadoop.fs.PingServer
```

### Hadoop FS

Run a simple Hadoop FS command:

```shell
hadoop fs -ls gs://$PROJECT-demo-bucket/
```

This scenario uses the simple [direct authentication](../concepts/authentication.md#direct-authentication) workflow,
where Hadoop directly requests an access token from the broker to access GCS.

### Yarn

Run a simple wordcount job:

```shell
hadoop jar /usr/lib/hadoop-mapreduce/hadoop-mapreduce-examples.jar wordcount \
  gs://apache-beam-samples/shakespeare/macbeth.txt \
  gs://$PROJECT-demo-bucket/wordcount/output-$(uuidgen)
```

This scenario uses the [delegated authentication](../concepts/authentication.md#delegated-authentication) workflow, where the Hadoop
client first requests a delegation token from the broker, then passes the delegation token to the
Yarn workers, which then call the broker again to trade the delegation token for access tokens to access GCS.

### Hive

Here are some sample Hive queries you can run using the `hive` command:

1.  Create a Hive table:

    ```shell
    hive -e "CREATE EXTERNAL TABLE transactions
            (SubmissionDate DATE, TransactionAmount DOUBLE, TransactionType STRING)
            STORED AS PARQUET
            LOCATION 'gs://$PROJECT-demo-bucket/datasets/transactions';"
    ```

2.  Run a simple `SELECT` query:

    ```shell
    hive -e "SELECT * FROM transactions LIMIT 5;"
    ```
    This simple query only uses direct authentication.

3.  Run a more complex query with some aggregations:

    ```shell
    hive -e "SELECT TransactionType, AVG(TransactionAmount) AS AverageAmount
             FROM transactions
             WHERE SubmissionDate = '2017-12-22'
             GROUP BY TransactionType;"
    ```
    This query is distributed across multiple tasks and therefore uses delegated
    authentication.

The same Hive queries can also be run using `beeline` as follows:

```shell
beeline -u "jdbc:hive2://localhost:10000/default;principal=hive/$(hostname -f)@$DATAPROC_REALM" \
  -e "CREATE EXTERNAL TABLE transactions
      (SubmissionDate DATE, TransactionAmount DOUBLE, TransactionType STRING)
      STORED AS PARQUET
      LOCATION 'gs://$PROJECT-demo-bucket/datasets/transactions';"

beeline -u "jdbc:hive2://localhost:10000/default;principal=hive/$(hostname -f)@$DATAPROC_REALM" \
  -e "SELECT * FROM transactions LIMIT 5;"

beeline -u "jdbc:hive2://localhost:10000/default;principal=hive/$(hostname -f)@$DATAPROC_REALM" \
  -e "SELECT TransactionType, AVG(TransactionAmount) AS AverageAmount
      FROM transactions
      WHERE SubmissionDate = '2017-12-22'
      GROUP BY TransactionType;"
```

To try different execution engines (Tez or MapReduce), add either one of the following
parameters:

*   For Tez:

    ```shell
    --hiveconf="hive.execution.engine=tez"
    ```

*   For Map Reduce:

    ```shell
    --hiveconf="hive.execution.engine=mr"
    ```

**Troubleshooting**:

If you get the following error when using hive:

```
FAILED: Execution Error, return code 1 from org.apache.hadoop.hive.ql.exec.DDLTask. MetaException(message:java.lang.RuntimeException:
User is not logged-in with Kerberos or cannot authenticate with the broker.
Kerberos error message: No valid credentials provided
(Mechanism level: No valid credentials provided (Mechanism level: Failed to find any Kerberos tgt)))
```

... try restarting the Hive Metastore:

```
sudo service hive-metastore restart
```

### SparkSQL

Follow these steps to test with SparkSQL:

1.  Stark a Spark Shell session:

    ```shell
    spark-shell --conf "spark.yarn.access.hadoopFileSystems=gs://$PROJECT-demo-bucket"
    ```

2.  Run the following Spark code:

    ```scala
    import org.apache.spark.sql.hive.HiveContext
    val hiveContext = new org.apache.spark.sql.hive.HiveContext(sc)
    hiveContext.sql("SELECT * FROM transactions LIMIT 5").show()
    ```

3.  When you've finished your testing, exit the session:

    ```shell
    :q
    ```

### Simulating the delegation token lifecycle

Hadoop offers different commands to simulate the delegation token lifecycle.

In Hadoop v2.X, which comes pre-installed with Cloud Dataproc, you can use the
`hdfs fetchdt` command to simulate each step in the lifecycle to create, renew,
and cancel delegation tokens:

1.  Get a delegation token for user `alice`:

    ```shell
    hdfs fetchdt -fs gs://$PROJECT-demo-bucket --renewer alice@$REALM ~/my.dt
    ```
    The delegation token is now stored in the `~/my.dt` file.

2.  Renew the delegation token:

    ```shell
    hdfs fetchdt -fs gs://$PROJECT-demo-bucket --renew ~/my.dt
    ```
    The token's lifetime has now been extended.

3.  Cancel the delegation token:

    ```shell
    hdfs fetchdt -fs gs://$PROJECT-demo-bucket --cancel ~/my.dt
    ```
    The token is now cancelled and made inoperable.

In Hadoop v3.X, the `hdfs fetchdt` command was deprecated and replaced with the `dtutil`
command. Cloud Dataproc currently doesn't support Hadoop v3, but if you have access to
a cluster with Hadoop v3, you can achieve the same tests as follows:

1.  Get a delegation token:

    ```shell
    hadoop dtutil get gs://$PROJECT-demo-bucket -alias my-alias -renewer alice@$REALM  ~/my.dt
    ```
2.  Renew the delegation token:

    ```shell
    hadoop dtutil renew -alias my-alias ~/my.dt
    ```
    
3.  Cancel the delegation token:

    ```shell
    hadoop dtutil cancel -alias my-alias ~/my.dt
    ```
