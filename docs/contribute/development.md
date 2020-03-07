## Development

### Creating a development container

You can use docker to create a container dedicated for development tasks.

Create the container by running this command **from the repository's root**:

```shell
./run.sh init_dev
```

This installs all the dependencies needed to build packages and run the tests.

### Building packages

To build all packages:

```shell
./run.sh build
```

To build an extension, for example the Redis caching backend:

```shell
./run.sh build -m redis
```

To build the broker connector:

```shell
./run.sh build -m connector
```

### Building containers

```shell
# Broker service
docker build -f ./code/broker-server/Dockerfile -t gcr.io/${PROJECT}/broker-server .
docker push gcr.io/$PROJECT/broker-server

# Authorizer
docker build -f ./code/authorizer/Dockerfile -t gcr.io/${PROJECT}/authorizer .
docker push gcr.io/$PROJECT/authorizer
```