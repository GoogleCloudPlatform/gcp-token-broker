## Load testing

This repository contains some load tests that use the [Locust](https://locust.io/) framework.

You can run the load tests from the sample Dataproc cluster that you created for the demo.

1.  SSH into the Dataproc master instance:

    ```shell
    gcloud compute ssh test-cluster-m --tunnel-through-iap
    ```

2.  Clone the project's repository:

    ```shell
    git clone https://github.com/GoogleCloudPlatform/gcp-token-broker
    cd gcp-token-broker/load-testing
    ```

3.  Install some dependencies:

    ```shell
    ./install.sh
    ```

4.  Create the Python gRPC stubs:

    ``shell
    python3 -m grpc_tools.protoc --proto_path=. --python_out=. --grpc_python_out=. brokerservice/protobuf/broker.proto
    ```

5.  Create a `settings.py` file using the provided template.

    ```shell
    cp settings.py.template settings.py
    ```

6.  Edit the `settings.py` to set appropriate values for your setup.

7.  To run the load tests in headless mode:

    ```shell
    ~/.local/bin/locust --no-web -c 1000 -r 10
    ```
    The `-c` corresponds to the total number of users, and `-r` the hatch rate
    (i.e. the number of new users spawned each passing second). To stop the tests,
    press `ctrl-c`.

8.  To run the tests using the Web UI, start the Locust server:

    ```shell
    ~/.local/bin/locust
    ```
    Then, in another terminal on your local machine, run the following command to set up
    a tunnel with the Dataproc master instance:

    ```shell
    gcloud beta compute start-iap-tunnel test-cluster-m 8089 \
    --local-host-port=localhost:8089
    --zone $ZONE
    ```
    Then open your browser at the address `http://localhost:8089`

Note: During the execution of load tests, you might see some errors: `Too many open files`.
This is because all users must read the Kerberos credentials from a temporary cache file,
and the limit of open files allowed by the OS might be reached. To increase the limit, run
the following command:

```shell
ulimit -n 32768
```
