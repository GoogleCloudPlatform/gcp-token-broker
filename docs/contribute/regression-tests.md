## Regression tests

Follow these steps to run the test suite:

1. Create a GCP project dedicated to running the tests. Do *not* re-use an existing project
   that might be used for other purposes. Running the test suite will create and delete
   GCP resources in the project, so it's important that this project is only used for running
   the tests.

2. Set an environment variable for your project ID (Replace **`[PROJECT_ID]`** with your project ID):
   ```shell
   PROJECT=[PROJECT_ID]
   ```

3. Set the project ID as the default for `gcloud`:

   ```shell
   gcloud config set project ${PROJECT}
   ```
4. Enable some Google APIs:

   ```shell
   gcloud services enable datastore.googleapis.com iam.googleapis.com cloudkms.googleapis.com
   ```
5. Activate the Cloud Datastore database for your project:

   ```shell
   gcloud app create --region=us-central
   ```
   Note: Cloud Datastore requires an active App Engine application, so you must create one by using this command.
6. Create a service account for the broker service:

   ```shell
   gcloud iam service-accounts create broker
   ```
7. Create a service account for a test user:

   ```shell
   gcloud iam service-accounts create alice-shadow
   ```
8. Allow the broker service account to generate access tokens on behalf of the test service account:

   ```shell
   gcloud iam service-accounts add-iam-policy-binding alice-shadow@${PROJECT}.iam.gserviceaccount.com \
     --role roles/iam.serviceAccountTokenCreator \
     --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
   ```
9. Add the Cloud Datastore user IAM role to allow the broker service account to read and write to the database:

   ```shell
   gcloud projects add-iam-policy-binding $PROJECT \
     --role roles/datastore.user \
     --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
   ```
10. Create a KMS keyring:

    ```shell
    gcloud kms keyrings create mykeyring --location global
    ```
11. Create a KMS key:

    ```shell
    gcloud kms keys create mykey --location global \
      --keyring mykeyring --purpose encryption
    ```

12. Give permission to the broker service account to use the keyring:

    ```shell
    gcloud kms keyrings add-iam-policy-binding \
      mykeyring --location global \
      --role roles/cloudkms.cryptoKeyEncrypterDecrypter \
      --member="serviceAccount:broker@${PROJECT}.iam.gserviceaccount.com"
    ```

13. Download a private JSON key for the broker service account:

   ```shell
   gcloud iam service-accounts keys create --iam-account \
     broker@${PROJECT}.iam.gserviceaccount.com \
     service-account-key.json
   ```

14. You can now run the tests as follows:

    To run the entire test suite:

    ```shell
    docker exec -it \
      --env APP_SETTING_GCP_PROJECT=${PROJECT} \
      --env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json  \
      broker-dev bash -c "mvn test"
    ```

    To run the tests for a specific component, for example the Cloud Datastore database backend:

    ```shell
    docker exec -it \
      --env APP_SETTING_GCP_PROJECT=${PROJECT} \
      --env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json  \
      broker-dev bash -c "mvn test --projects apps/core,apps/extensions/database/cloud-datastore"
    ```

    To run a specific test class, pass the `-DfailIfNoTests=false -Dtest=[NAME_OF_TEST_CLASS]` properties, for example:

    ```shell
    docker exec -it \
      broker-dev bash -c "mvn test --projects apps/core,apps/broker \
      -DfailIfNoTests=false -Dtest=ValidationTest"
    ```

    To run a specific test method, pass the `-DfailIfNoTests=false -Dtest=[NAME_OF_TEST_CLASS]#[NAME_OF_TEST_METHOD]`
    properties, for example:

    ```shell
    docker exec -it \
      broker-dev bash -c "mvn test --projects apps/core,apps/broker \
      -DfailIfNoTests=false -Dtest=ValidationTest#testValidateScope"
    ```

**Note:** The `APP_SETTING_GCP_PROJECT` and `GOOGLE_APPLICATION_CREDENTIALS` environment variables
are only necessary for the tests that rely on GCP APIs (e.g. for the Cloud Datastore backend).
Other tests do not need those variables.

### Inspecting the test coverage

1. Follow the steps in the "Running the tests" section to set up your test environment.
2. Run the test with the `test-coverage` profile:

   ```shell
      docker exec -it \
        --env APP_SETTING_GCP_PROJECT=${PROJECT} \
        --env GOOGLE_APPLICATION_CREDENTIALS=/base/service-account-key.json  \
        broker-dev bash -c "mvn test -P test-coverage"
   ```
3. Start a web server inside the container:

   ```shell
   docker exec -it broker-dev bash -c "python3 -m http.server 7070"
   ```
4. You can now browse the coverage reports for each component, for example:
   * Broker service: http://localhost:7070/apps/broker/target/site/jacoco/index.html
   * Cloud Datastore backend: http://localhost:7070/apps/extensions/database/cloud-datastore/target/site/jacoco/index.html
