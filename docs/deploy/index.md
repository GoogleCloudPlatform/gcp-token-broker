This guide describes how to set up a demo environment to test the broker service based on Kubernetes Engine. (Another
version of this guide using Cloud Run is also available in the "cloud-run" branch. Click [here](https://github.com/GoogleCloudPlatform/gcp-token-broker/blob/cloud-run/docs/deploy/index.md)
if you'd like to try out that version instead).

**_Important note:_ The following instructions are provided only as a reference
to create the demo environment and _should not_ be used as-is in production.**

The demo environment will help you understand and test the broker's functionality in a practical way.
Once you become familiar with the broker's codebase and functionality, you can look to adapt the demo
to your production or staging environments (For more details on that, refer to the
"[Production considerations](#production-considerations)" section).

Notes about the architecture:

*   The broker's server application is implemented in Java and is deployed with [Cloud Run](https://cloud.google.com/run).
*   Interactions between clients and the broker is done with gRPC and protocol buffers.
*   The Origin KDC is the source of truth for Kerberos users. Alternatively it could be replaced with
    an Active Directory or Open LDAP instance.
*   All machines for the KDC and clients are deployed in private networks with RFC 1918 IP addresses.
    [Cloud NAT](https://cloud.google.com/nat/docs/overview) gateways are deployed for cases where machines need
    to access the internet. Private connectivity between Hadoop client machines (e.g. on [Compute Engine](https://cloud.google.com/compute/)
    or [Cloud Dataproc](https://cloud.google.com/dataproc/)) and the Origin KDC is
    established over [VPC peering](https://cloud.google.com/vpc/docs/vpc-peering). Alternatively, private connectivity could also be
    established with [Cloud VPN](https://cloud.google.com/vpn/docs/concepts/overview) or [Shared VPC](https://cloud.google.com/vpc/docs/shared-vpc).
    [Google Private Access](https://cloud.google.com/vpc/docs/private-access-options#pga) is enabled on the VPC subnets to allow machines to access
    Google services like Cloud Datastore, Cloud Storage or Cloud KMS.

### Prerequisites

Before you start, you must set up some prerequisites for the demo:

1.  Register a new domain name with your preferred domain registrar. This is recommended so you can
    test in a self-contained environment.
2.  [Create a new GSuite organization](https://gsuite.google.com/signup/gcpidentity/welcome) associated with the new domain name.
3.  [Create](https://support.google.com/a/answer/33310?hl=en) 3 new non-admin users in the organization (e.g. "alice", "bob", and "john").
4.  Create a new GCP project under the GSuite organization and [enable billing](https://cloud.google.com/billing/docs/how-to/modify-project).
5.  Install some tools on your local machine (The versions indicated below are the ones that have been officially tested.
    Newer versions might work but are untested):
    *   [Terraform](https://learn.hashicorp.com/terraform/getting-started/install.html) v0.12.24
    *   [Google Cloud SDK](https://cloud.google.com/sdk/install) v285.0.1

### Deploying the demo architecture

The demo environment is comprised of multiple components and GCP products (Cloud Datastore, Cloud Memorystore,
VPCs, firewall rules, etc.), which are automatically deployed using terraform.

Follow these steps to deploy the demo environment to GCP:

1.  Log in as the Google user who owns the GCP project:

    ```shell
    gcloud auth application-default login
    ```
    
2.  Run the following commands to set some default configuration values for `gcloud`.
    Replace **`[your-project-id]`** with your GCP project ID, and **`[your-zone-of-choice]`**
    with your preferred zone (See list of [availables zones](https://cloud.google.com/compute/docs/regions-zones/#available)):

    ```shell
    gcloud config set project [your-project-id]
    gcloud config set compute/zone [your-zone-of-choice]
    ```

3.  Set some environment variables:

    ```shell
    export PROJECT=$(gcloud info --format='value(config.project)')
    export ZONE=$(gcloud info --format='value(config.properties.compute.zone)')
    export REGION=${ZONE%-*}
    ```

4.  Change into the `terraform` directory:

    ```shell
    cd terraform
    ```

5.  Create a Terraform variables file:

    ```shell
    cat > ${PROJECT}.tfvars << EOL
    gcp_project="${PROJECT}"
    gcp_region="${REGION}"
    gcp_zone="${ZONE}"
    EOL
    ```
 
6.  Edit the variables file that you created in the previous step and add the following values:

    ```conf
    datastore_region = "[your.datastore.region]"
    gsuite_domain = "[your.domain.name]"
    origin_realm = "[YOUR.REALM.NAME]"
    test_users = ["alice", "bob", "john"]
    ```

    Notes:
    *   `datastore_region` is the region for your Cloud Datastore database (e.g. `us-west2`). See the list of
        [available regions](https://cloud.google.com/datastore/docs/locations#location-r) for Cloud Datastore.
    *   `gsuite_domain` is the domain name (e.g. "your-domain.com") that you registered in the [Prerequisites](#prerequisites)
        section for your GSuite organization.
    *   `origin_realm` is the Kerberos realm (e.g. "YOUR-DOMAIN.COM") that you wish
        to use for your test users. This value can be a totally arbitrary string, and is
        generally made of UPPERCASE letters.
    *   Replace the `test_users` with the usernames of the three users that you created in the
        [Prerequisites](#prerequisites) section.

7.  Create a new Terraform workspace:

    ```shell
    terraform workspace new ${PROJECT}
    ```

8.  Initialize the terraform deployment:

    ```shell
    terraform init
    ```
    
9.  Run the deployment:
 
    ```shell
    terraform apply -var-file ${PROJECT}.tfvars
    ```
    
10. Return to the root of the repository:

    ```shell
    cd ..
    ```

### Configuring the OAuth client

1.  Create an OAuth consent screen:
    *   Go to: <https://console.cloud.google.com/apis/credentials/consent>
    *   For "Application type", select "Internal".
    *   For "Application name", type "GCP Token Broker".
    *   For "Scopes for Google APIs", click "Add scope", then search for
        "Google Cloud Storage JSON API", then tick the checkbox for
        "auth/devstorage.read_write", then click "Add".
    *   For "Authorized domains":
        -   Type the authorizer app's [top private domain](https://github.com/google/guava/wiki/InternetDomainNameExplained#public-suffixes-and-private-domains),
        -   **Press `Enter`** on your keyboard to add the top private domain to the list.
    *   Click "Save".

3.  Create a new OAuth client ID:
    *   Go to: <https://console.cloud.google.com/apis/credentials>
    *   Click "Create credentials" > "OAuth client ID"
    *   For "Application type", select "Web application".
    *   For "Name", type "GCP Token Broker".
    *   Click "Create".
    *   Click "Ok" to close the confirmation popup.
    *   Click the "Download JSON" icon for your client ID.
    *   Upload the file to Secret Manager (Replace **`[CLIENT_SECRET.JSON]`** with the name of the file your downloaded in the
        previous step):
        ```shell
        gcloud secrets create oauth-client --replication-policy="automatic"
        gcloud secrets versions add oauth-client --data-file=[CLIENT_SECRET.JSON]
        ```
    *   You can now delete the file from your local filesystem (Replace  **`[CLIENT_SECRET.JSON]`** with the actual file
        name):
        ```shell
        rm [CLIENT_SECRET.JSON]
        ```

### Generating the data encryption key (DEK)

1.  Generate the data encryption key (DEK) for the Cloud KMS encryption backend:

    ```shell
    export ZONE=$(gcloud info --format='value(config.properties.compute.zone)')
    export REGION=${ZONE%-*}
    curl "https://repo1.maven.org/maven2/com/google/cloud/broker/broker-core/${BROKER_VERSION}/broker-core-${BROKER_VERSION}-jar-with-dependencies.jar" -o broker-core-${BROKER_VERSION}-jar-with-dependencies.jar
    curl "https://repo1.maven.org/maven2/com/google/cloud/broker/encryption-backend-cloud-kms/${BROKER_VERSION}/encryption-backend-cloud-kms-${BROKER_VERSION}-jar-with-dependencies.jar" -o encryption-backend-cloud-kms-${BROKER_VERSION}-jar-with-dependencies.jar
    java -cp broker-core-${BROKER_VERSION}-jar-with-dependencies.jar:encryption-backend-cloud-kms-${BROKER_VERSION}-jar-with-dependencies.jar \
      com.google.cloud.broker.encryption.GenerateDEK \
      file://dek.json \
      projects/${PROJECT}/locations/${REGION}/keyRings/broker-key-ring/cryptoKeys/broker-key
    ```

2.  Upload the DEK to Secret Manager:

    ```shell
    gcloud secrets create dek --replication-policy="automatic"
    gcloud secrets versions add dek --data-file=dek.json
    ```
    
3.  You can now delete the DEK from your local filesystem:

    ```shell
    rm dek.json
    ```

### Using the Authorizer

The Authorizer is a simple Web UI that users must use to authorize the broker. Follow these steps to deploy and use the
Authorizer:

1.  Deploy the Authorizer app:

    ```shell
    export BROKER_VERSION=$(cat VERSION)
    export PROJECT=$(gcloud info --format='value(config.project)')
    export ZONE=$(gcloud info --format='value(config.properties.compute.zone)')
    export REGION=${ZONE%-*}
    gcloud run deploy authorizer \
      --image gcr.io/gcp-token-broker/authorizer:${BROKER_VERSION} \
      --platform managed \
      --allow-unauthenticated \
      --service-account "broker@${PROJECT}.iam.gserviceaccount.com" \
      --region ${REGION} \
      --set-env-vars=CONFIG_BASE64=$(base64 deploy/${PROJECT}/authorizer.conf | tr -d '\n')
    ```
    
2.  Retrieve the app's URL:
    
    ```shell
    gcloud run services describe authorizer \
      --platform managed \
      --region ${REGION} \
      --format "value(status.url)"
    ```
    
    Take note of this URL. You will need it in the next step.

3.  Configure the OAuth client:
    *   Go to: <https://console.cloud.google.com/apis/credentials>
    *   Click on the "GCP Token Broker" OAuth 2.0 Client ID.
    *   Under "Authorized redirect URIs", click "Add URI".
    *   Type the following (Replace **`[APP_ID]`** with the ID in the url that you noted in the previous step):
        `https://authorizer-[APP_ID].a.run.app/google/oauth2callback`
    * Click "Save".

2.  Open the authorizer page in your browser: `https://authorizer-[APP_ID].a.run.app` (Replace **`[APP_ID]`** with the
    ID in your URL).

3.  Click "Authorize". You are redirected to the Google login page.

4.  Enter the credentials for one of the three users you created in the [Prerequisites](#prerequisites) section.

5.  Read the consent form, then click "Allow". You are redirected back to
    the authorizer page, and are greeted with a "Success" message. The
    broker now has authority to generate GCP access tokens on the user's behalf.


### Starting the broker service

Run the following command to deploy the broker service:

```shell
export BROKER_VERSION=$(cat VERSION)
export PROJECT=$(gcloud info --format='value(config.project)')
export ZONE=$(gcloud info --format='value(config.properties.compute.zone)')
export REGION=${ZONE%-*}
gcloud run deploy broker-server \
  --image gcr.io/gcp-token-broker/broker-server:${BROKER_VERSION} \
  --platform managed \
  --allow-unauthenticated \
  --region ${REGION} \
  --service-account broker@${PROJECT}.iam.gserviceaccount.com \
  --set-env-vars=CONFIG_BASE64=$(base64 deploy/${PROJECT}/broker-server.conf | tr -d '\n')
```

### Creating a Dataproc cluster

In this section, you create a Dataproc cluster that can be used to run Hadoop jobs and interact with the broker.

1.  Set an environment variable for the Kerberos realm (Replace **`[ORIGIN.REALM.COM]`** with the
    same Kerberos realm you used in the Terraform variables file (with the `.tfvars` extension):

    ```shell
    export REALM=[ORIGIN.REALM.COM]
    ```

2.  Set a few more environment variables:

    ```shell
    export PROJECT=$(gcloud info --format='value(config.project)')
    export ZONE=$(gcloud info --format='value(config.properties.compute.zone)')
    export REGION=${ZONE%-*}
    export BROKER_URI=$(gcloud run services describe broker-server --platform managed --region ${REGION} --format "value(status.url)")
    export BROKER_PRINCIPAL="broker"
    export BROKER_VERSION=$(cat VERSION)
    export INIT_ACTION="gs://gcp-token-broker/broker-hadoop-connector.${BROKER_VERSION}.sh"
    export CONNECTOR_JAR_URL="https://repo1.maven.org/maven2/com/google/cloud/broker/broker-hadoop-connector/hadoop2-${BROKER_VERSION}/broker-hadoop-connector-hadoop2-${BROKER_VERSION}-jar-with-dependencies.jar"
    ```

4.  Create the Dataproc cluster:

    ```shell
    gcloud dataproc clusters create test-cluster \
      --single-node \
      --no-address \
      --zone ${ZONE} \
      --region ${REGION} \
      --subnet client-subnet \
      --image-version 1.4 \
      --bucket ${PROJECT}-staging \
      --scopes cloud-platform \
      --service-account "dataproc@${PROJECT}.iam.gserviceaccount.com" \
      --kerberos-config-file deploy/${PROJECT}/kerberos-config.yaml \
      --initialization-actions ${INIT_ACTION} \
      --metadata "gcp-token-broker-uri=${BROKER_URI}" \
      --metadata "gcp-token-broker-kerberos-principal=${BROKER_PRINCIPAL}" \
      --metadata "origin-realm=${REALM}" \
      --metadata "connector-jar-url=${CONNECTOR_JAR_URL}"
    ```

    *Note:* The command creates a [single-node](https://cloud.google.com/dataproc/docs/concepts/configuring-clusters/single-node-clusters)
    Cloud Dataproc cluster, which is sufficient for the scope of this demo. In production, it is recommended to use a multi-node
    cluster instead.

### Uploading the keytab

The broker service needs a keytab to authenticate incoming requests.

1.  Download the keytab from the Dataproc cluster's realm:

    ```shell
    gcloud compute ssh test-cluster-m \
      -- "sudo cat /etc/security/keytab/broker.keytab" | perl -pne 's/\r$//g' > broker.keytab

2.  Upload the keytab to Secret Manager:

    ```shell
    gcloud secrets create keytab --replication-policy="automatic"
    gcloud secrets versions add keytab --data-file=broker.keytab
    ```

3.  You can now delete the keytab from your local filesystem:

    ```shell
    rm broker.keytab
    ```
    
4.  Reload the broker service so it can fetch the keytab:

    ```shell
    export PROJECT=$(gcloud info --format='value(config.project)')
    export ZONE=$(gcloud info --format='value(config.properties.compute.zone)')
    export REGION=${ZONE%-*}
    gcloud run deploy broker-server \
      --image gcr.io/gcp-token-broker/broker-server:${BROKER_VERSION} \
      --platform managed \
      --allow-unauthenticated \
      --region ${REGION} \
      --service-account broker@${PROJECT}.iam.gserviceaccount.com \
      --set-env-vars=CONFIG_BASE64=$(base64 deploy/${PROJECT}/broker-server.conf)
    ```
    
5.  You are now ready to do some testing. Refer to the [tutorials](../tutorials/index.md) section to run
    some sample Hadoop jobs and try out the broker's functionality.

## Broker application logs

Follow these steps to view the broker application logs in Stackdriver:

1.  Open the logs viewer in Stackdriver: <https://console.cloud.google.com/logs/viewer>

2.  Click the down arrow in the text search box, then click "Convert to advanced filter".

3.  Type the following in the text search box:

    ```conf
    resource.type="k8s_container"
    resource.labels.cluster_name="broker"
    resource.labels.namespace_name="default"
    labels.k8s-pod/run="broker-server"
    ```

4.  Click "Submit Filter".

## Production considerations

This section describes some tips to further improve the deployment process, performance, and
security in production environments.

### Deployment process

#### Configuration management

Note that the Terraform scripts provided in this repository are provided only as a reference to
set up a demo environment. You can use those scripts as a starting point to create your own
scripts for Terraform or your preferred configuration management tool to deploy the broker
service to production or staging environment.

### Performance optimizations

#### Caching

Be sure to turn on [caching](../concepts/caching.md) to improve performance.

#### Scalable database

To do its work, the broker needs to store some state, most notably refresh tokens and broker session details.
[Cloud Datastore](https://cloud.google.com/datastore) is a great option because of its high scalability
and ease of use. For extreme loads, consider using [Cloud Bigtable](https://cloud.google.com/bigtable/)
instead for its sub-10ms latency. You can select your preferred database backend with the
`DATABASE_BACKEND` setting.

### Security hardening

This section describes different ways to further harden security for the deployment.

#### Project structure

The broker service has a lot of power as it holds sensitive secrets (e.g. refresh tokens)
and has the capacity to generate access tokens for other users. Therefore it is highly
recommended to keep the broker and its core components (Cloud Run, cache, database, etc)
in a separate project, and to only allow a privileged group of admin users to access its
resources. Client machines can be allowed to access the broker service's API through private
network connectivity and via Kerberos authentication.

#### Transport encryption

Be sure to turn on [TLS](../concepts/tls.md).

#### Storage encryption

Be sure to turn on [encryption](../concepts/encryption.md).
