## Regression tests

Follow these steps to run the test suite:

1.  Create a GCP project dedicated to running the tests. Do *not* re-use an existing project
    that might be used for other purposes. Running the test suite will create and delete
    GCP resources in the project, so it's important that this project is only used for running
    the tests.

2.  Set an environment variable for your project ID (Replace **`[PROJECT_ID]`** with your project ID):
    ```shell
    PROJECT=[PROJECT_ID]
    ```

3.  Set the project ID as the default for `gcloud`:

    ```shell
    gcloud config set project ${PROJECT}
    ```
    
4.  Enable some Google APIs:

    ```shell
    gcloud services enable datastore.googleapis.com iam.googleapis.com cloudkms.googleapis.com secretmanager.googleapis.com
    ```

5.  Create a test bucket:

    ```shell
    gsutil mb gs://${PROJECT}-testbucket
    ```

6.  Allow the broker service account to access the bucket:

    ```shell
    gsutil iam ch serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com:objectAdmin gs://${PROJECT}-testbucket
    ```

7.  Activate the Cloud Datastore database for your project:

    ```shell
    gcloud app create --region=us-central
    ```
    Note: Cloud Datastore requires an active App Engine application, so you must create one by using this command.

8.  Create a service account for the broker service:

    ```shell
    gcloud iam service-accounts create broker
    ```

9.  Create a service account for a test user:

    ```shell
    gcloud iam service-accounts create alice-shadow
    ```

10. Allow the broker service account to sign JWTs for itself (This is necessary to test domain-wide delegation):

    ```shell
    gcloud iam service-accounts add-iam-policy-binding broker@${PROJECT}.iam.gserviceaccount.com \
      --role roles/iam.serviceAccountTokenCreator \
      --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
    ```

11. Allow the broker service account to generate access tokens on behalf of the test service account:

    ```shell
    gcloud iam service-accounts add-iam-policy-binding alice-shadow@${PROJECT}.iam.gserviceaccount.com \
      --role roles/iam.serviceAccountTokenCreator \
      --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
    ```

12. Add the Cloud Datastore user IAM role to allow the broker service account to read and write to the database:

    ```shell
    gcloud projects add-iam-policy-binding $PROJECT \
      --role roles/datastore.user \
      --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
    ```

13. Create a KMS keyring:

    ```shell
    gcloud kms keyrings create mykeyring --location global
    ```

14. Create a KMS key:

    ```shell
    gcloud kms keys create mykey --location global \
      --keyring mykeyring --purpose encryption
    ```

15. Give permission to the broker service account to use the keyring:

    ```shell
    gcloud kms keyrings add-iam-policy-binding \
      mykeyring --location global \
      --role roles/cloudkms.cryptoKeyEncrypterDecrypter \
      --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
    ```

16. Download a private JSON key for the broker service account:

    ```shell
    gcloud iam service-accounts keys create --iam-account \
      broker@${PROJECT}.iam.gserviceaccount.com \
      service-account-key.json
    ```

17. Create a secret in Secret Manager:

    ```shell
    gcloud secrets create secretstuff --replication-policy="automatic"
    echo -n "This is secret stuff" | gcloud secrets versions add secretstuff --data-file=-
    ```

18. Allow the broker service account to access Secret Manager:

    ```shell
    gcloud projects add-iam-policy-binding $PROJECT \
      --role roles/secretmanager.secretAccessor \
      --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
    ```

19. Start a [development container](development.md):

    ```shell
    ./run.sh init_dev
    ```

20. You can now run the tests as follows:

    To run the entire test suite (Replace `[ORG]` with your test GSuite organization's domain and `[ORG_ADMIN]` with
    the email address of an admin of your GSuite organization):

    ```shell
    ./run.sh test -p ${PROJECT} -ga [ORG_ADMIN] -gd [ORG_DOMAIN]
    ```

    Notes:
    *   The `-ga` and `-gd` parameters are only necessary for some tests.
    *   The `-p` parameter and `GOOGLE_APPLICATION_CREDENTIALS` environment variable are only necessary for the tests
        that rely on GCP APIs (e.g. for the Cloud Datastore backend).

    To run the tests for a specific component, for example the connector:

    ```shell
    ./run.sh test -m connector
    ```

    To run a specific test class:

    ```shell
    ./run.sh test -m connector -t BrokerAccessTokenProviderTest
    ```

    To run a specific test method:

    ```shell
    ./run.sh test -m connector -t BrokerAccessTokenProviderTest#testProviderRefresh
    ```

### Inspecting the test coverage

1.  Follow the steps in the "Running the tests" section to set up your test environment.

2.  Run the test with the `test-coverage` profile:

    ```shell
       docker exec -it \
         --env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json  \
         broker-dev bash -c "mvn test -Dgcp-project=${PROJECT} -P test-coverage"
    ```

3.  Start a web server inside the container:

    ```shell
    docker exec -it broker-dev bash -c "python3 -m http.server 7070"
    ```

4.  You can now browse the coverage reports for each component, for example:
    *   Broker service: <http://localhost:7070/code/broker-server/target/site/jacoco/index.html>
    *   Cloud Datastore backend: <http://localhost:7070/code/extensions/database/cloud-datastore/target/site/jacoco/index.html>
