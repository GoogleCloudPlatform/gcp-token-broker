### Creating a development container

You can use docker to create a container dedicated for development tasks.

1. Create the container by running this command **from the repository's root**:

   ```shell
   docker run -it -v $PWD:/base -w /base -p 7070:7070 --detach --name broker-dev ubuntu:18.04
   ```

2. Install the required dependencies in the container:

   ```shell
   docker exec -it broker-dev bash -- code/broker-server/install-dev.sh
   ```

This installs all the dependencies needed to build packages and run the tests.

### Building packages

To build all packages:

```shell
docker exec -it broker-dev bash -c 'mvn package -DskipTests'
```

To build an extension, for example the Redis caching backend:

```shell
docker exec -it broker-dev bash -c "mvn package -DskipTests --projects code/core,code/extensions/caching/redis"
```

To build the broker connector for a specific version of Hadoop (possible options: `hadoop2` and `hadoop3`):

```shell
docker exec -it broker-dev bash -c "mvn package -DskipTests --projects connector -P hadoop2"
```

Note: Some packages depend on the `broker-core` package, which is why you must pass the `code/core` parameter
when you build those packages.