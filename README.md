# GCP Token Broker

_**Notice**: This is an **early** release of the GCP Token Broker. This project might be changed in
backward-incompatible ways and is not subject to any SLA or deprecation policy._

## About this project

The GCP Token Broker enables end-to-end Kerberos security and Cloud IAM integration for Hadoop
workloads on [Google Cloud Platform](https://cloud.google.com/) (GCP).

This project aims to achieve the following goals:

* Bridge the gap between Kerberos and Cloud IAM to allow users to log in with Kerberos and access GCP resources.
* Enable multi-tenancy for Hadoop clusters on Compute Engine and Cloud Dataproc.
* Enable user impersonation by Hadoop services such as Hive, Presto, or Oozie.

This project also strives to address the following requirements, which many enterprise customers have when
they're looking to migrate on-premise workloads to the cloud:

* All access to GCP resources (Cloud Storage, Google BigQuery, Cloud Bigtable, etc) should be attributable
  to the individual users who initiated the requests.
* No long-lived credentials should be stored on client machines or worker nodes.
* Cause as few changes as possible to existing on-premise security systems and user workflows.

## Documentation

See the full documentation [here](docs/index.md).

## Repository's contents

This repository contains:

- `apps`: Server applications, including:
  - `authorizer`: Web UI for the OAuth flow that users must go through to authorize the broker service.
  - `broker`: The broker service itself.
- `deploy`: Helm charts for deploying the broker service and the authorizer app to a Kubernetes cluster.
- `docs`: Technical documentation for this project.
- `connector`: Extension for the GCS Connector to allow Hadoop to communicate with the broker.
- `init-action`: Initialization action to install the broker dependencies in
  a Cloud Dataproc cluster.
- `load-testing`: Scripts for running loads tests for the broker service.
- `terraform`: Terraform scripts to deploy a sample demo environment. This is
  provided only as a reference and **_should not_** be used as-is in production.

## Roadmap

Included in the current **early** release:

* Full lifecycle of Hadoop-style delegation tokens: creation, renewal, cancellation.
* Support for Hadoop-style proxy users.
* Authentication backend: Kerberos.
* Target GCP service: Cloud Storage.
* Database backends: Cloud Datastore, JDBC.
* Cache backend: Redis on Cloud Memorystore.

Plans for the **stable** releases:

* Performance optimizations.
* API stabilization.

Plans for **future** releases:

* Target GCP services: BigQuery, Cloud Bigtable, Cloud PubSub.
* Database backends: Cloud Firestore, Cloud Bigtable.
* Cache backends: Memcached, Cloud Bigtable.
* Support for more authentication backends: TBD.

## How to Contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow. See the [contributing guide](docs/contribute/index.md)
for more details.
