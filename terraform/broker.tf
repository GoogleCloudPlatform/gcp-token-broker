# Copyright 2019 Google LLC
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

locals {
  dataproc_realm = "${upper(var.gcp_zone)}.C.${upper(var.gcp_project)}.INTERNAL"
}


resource "google_service_account" "broker" {
  account_id = "broker"
  display_name = "Broker's service account"
}

# Allow token creator role on itself. Necessary to enable the domain-wide
# delegation flow.
resource "google_service_account_iam_member" "broker_self_token_creator" {
  service_account_id = "${google_service_account.broker.name}"
  role        = "roles/iam.serviceAccountTokenCreator"
  member      = "serviceAccount:${google_service_account.broker.email}"
}


// VPC -------------------------------------------------------------

resource "google_compute_network" "broker" {
  name                    = "broker-network"
  auto_create_subnetworks = "false"
  depends_on = ["google_project_service.service_compute"]
}

resource "google_compute_subnetwork" "broker_cluster_subnet" {
  name          = "broker-cluster-subnet"
  ip_cidr_range = "${var.broker_subnet_cidr}"
  region        = "${var.gcp_region}"
  network       = "${google_compute_network.broker.self_link}"
  private_ip_google_access = true
}

resource "google_compute_firewall" "broker_allow_external_kdcs" {
  name    = "broker-allow-external-kdcs"
  network = "${google_compute_network.broker.name}"

  allow {
    protocol = "icmp"
  }
  allow {
    protocol = "tcp"
    ports    = ["88"]
  }
  allow {
    protocol = "udp"
    ports    = ["88"]
  }
  source_ranges = [
    "${var.origin_subnet_cidr}",
    "${var.client_subnet_cidr}"
  ]
}

resource "google_compute_firewall" "broker_allow_ssh" {
  name    = "broker-allow-ssh"
  network = "${google_compute_network.broker.name}"

  allow {
    protocol = "icmp"
  }
  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
  source_ranges = [
    "35.235.240.0/20" // For IAP tunnel (see: https://cloud.google.com/iap/docs/using-tcp-forwarding)
  ]
}

resource "google_compute_firewall" "broker_allow_internal" {
  name    = "broker-allow-internal"
  network = "${google_compute_network.broker.name}"

  allow {
    protocol = "icmp"
  }
  allow {
    protocol = "tcp"
    ports    = ["1-65535"]
  }
  allow {
    protocol = "udp"
    ports    = ["1-65535"]
  }
  source_ranges = [
    "${var.broker_subnet_cidr}"
  ]
}

resource "google_compute_firewall" "broker_allow_http" {
  name    = "broker-allow-http"
  network = "${google_compute_network.broker.name}"

  allow {
    protocol = "icmp"
  }
  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }
  source_ranges = [
    "${var.client_subnet_cidr}"
  ]
}

// NAT gateway -----------------------------------------------

resource "google_compute_router" "broker" {
  name    = "broker"
  network = "${google_compute_network.broker.self_link}"
}

resource "google_compute_router_nat" "broker_nat" {
  name                               = "broker"
  router                             = "${google_compute_router.broker.name}"
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}


// Datastore ------------------------------------------------------

resource "google_project_iam_member" "datastore_user" {
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.broker.email}"
}

# Create App Engine so the broker can use Datastore
resource "google_app_engine_application" "app" {
  location_id = "${var.datastore_region}"
}


// GKE cluster ----------------------------------------------------

# Force creation of the container registry's bucket ("artifacts.${var.gcp_project}.appspot.com")
# by pushing a sample image. This allows setting permissions to that bucket later on.
resource "null_resource" "create_container_registry_bucket" {
  provisioner "local-exec" {
    command = <<EOT
      gcloud -q auth configure-docker \
      && docker pull alpine:latest \
      && docker tag alpine gcr.io/${var.gcp_project}/alpine \
      && docker push gcr.io/${var.gcp_project}/alpine
    EOT
  }
  depends_on = ["google_project_service.service_containerregistry"]
}

# Give access to the container registry's bucket
resource "google_storage_bucket_iam_member" "container_registry_bucket" {
  bucket  = "artifacts.${var.gcp_project}.appspot.com"
  role    = "roles/storage.objectViewer"
  member  = "serviceAccount:${google_service_account.broker.email}"
  depends_on = [
    "null_resource.create_container_registry_bucket"
  ]
}

resource "google_project_iam_member" "log_writer" {
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.broker.email}"
}

resource "google_project_iam_member" "metric_writer" {
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.broker.email}"
}

resource "google_container_cluster" "broker" {
  provider = "google-beta"  # Need beta release to enable private_cluster_config
  name = "broker"
  network = "${google_compute_network.broker.self_link}"
  subnetwork = "${google_compute_subnetwork.broker_cluster_subnet.self_link}"
  initial_node_count = "${var.broker_initial_node_count}"

  node_config {
    oauth_scopes = ["cloud-platform"]
    service_account = "${google_service_account.broker.email}"
  }
  ip_allocation_policy {
      cluster_ipv4_cidr_block = "${var.broker_pod_cidr}"
      services_ipv4_cidr_block = "${var.broker_service_cidr}"
  }
  private_cluster_config {
    # enable_private_endpoint = true  # FIXME
    enable_private_nodes = true
    master_ipv4_cidr_block = "${var.broker_master_cidr}"
  }
  master_authorized_networks_config {
      cidr_blocks {
          cidr_block = "${var.broker_master_authorized_cidr}"
      }
  }
  depends_on = [
    "google_project_iam_member.log_writer",
    "google_project_iam_member.metric_writer",
  ]
}


// Redis cache -----------------------------------------------------

resource "google_redis_instance" "cache" {
  name = "broker-cache"
  memory_size_gb = 1
  reserved_ip_range = "10.1.0.0/29"
  authorized_network = "${google_compute_network.broker.self_link}"
  depends_on = ["google_project_service.service_redis"]
}


// Encryption -------------------------------------------------------

resource "google_kms_key_ring" "broker_key_ring" {
  name     = "broker-key-ring"
  location = "${var.gcp_region}"
  depends_on = ["google_project_service.service_kms"]
}

resource "google_kms_crypto_key" "refresh_token_key" {
  name            = "refresh-token-key"
  key_ring        = "${google_kms_key_ring.broker_key_ring.self_link}"
}

resource "google_kms_crypto_key" "access_token_cache_key" {
  name            = "access-token-cache-key"
  key_ring        = "${google_kms_key_ring.broker_key_ring.self_link}"
}

resource "google_kms_crypto_key" "delegation_token_key" {
  name            = "delegation-token-key"
  key_ring        = "${google_kms_key_ring.broker_key_ring.self_link}"
}

resource "google_kms_key_ring_iam_binding" "key_ring" {
  key_ring_id = "${var.gcp_project}/${var.gcp_region}/${google_kms_key_ring.broker_key_ring.name}"
  role        = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  members = [
    "serviceAccount:${google_service_account.broker.email}"
  ]
}


// Authorizer application ---------------------------------------------------

resource "google_compute_global_address" "authorizer" {
  name = "authorizer-ip"
  address_type = "EXTERNAL"
  depends_on = ["google_project_service.service_compute"]
}


// Helm config values --------------------------------------------------------

resource "local_file" "helm_values" {
    content = <<EOT
###############################################################
# Automatically generated by Terraform. Do not edit manually. #
###############################################################

# Broker config -----------------------
broker:
  image: 'gcr.io/${var.gcp_project}/broker'
  app:
    settings:
      GCP_PROJECT: '${var.gcp_project}'
      GCP_REGION: '${var.gcp_region}'
      PROXY_USER_WHITELIST: 'hive/test-cluster-m.${var.gcp_zone}.c.${var.gcp_project}.internal@${local.dataproc_realm}'
      BROKER_REALM: '${var.origin_realm}'
      ORIGIN_REALM: '${var.origin_realm}'
      DOMAIN_NAME: '${var.domain}'
      BROKER_SERVICE_HOSTNAME: '${var.broker_service_hostname}'
      REDIS_CACHE_HOST: '${google_redis_instance.cache.host}'
      LOGGING_LEVEL: 'INFO'
  service:
    port: '${var.broker_service_port}'
    loadBalancerIP: '${var.broker_service_ip}'
    loadBalancerSourceRanges:
    # Allow access only from client machines
    - '${var.client_subnet_cidr}'

# Authorizer config -----------------------
authorizer:
  image: 'gcr.io/${var.gcp_project}/authorizer'
  app:
    settings:
      GCP_PROJECT: '${var.gcp_project}'
      GCP_REGION: '${var.gcp_region}'
      DOMAIN_NAME: '${var.domain}'
  ingress:
    host: '${var.authorizer_hostname}'
EOT
    filename = "../deploy/values_override.yaml"
}