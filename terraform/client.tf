# Copyright 2020 Google LLC
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

resource "google_service_account" "dataproc" {
  account_id = "dataproc"
  display_name = "Dataproc's service account"
}


// VPC -----------------------------------------------

resource "google_compute_network" "client" {
  name                    = "client-network"
  auto_create_subnetworks = "false"
  depends_on = ["google_project_service.service_compute"]
}

resource "google_compute_subnetwork" "client_subnet" {
  name          = "client-subnet"
  ip_cidr_range = "${var.client_subnet_cidr}"
  region        = "${var.gcp_region}"
  network       = "${google_compute_network.client.self_link}"
  private_ip_google_access = true
}

resource "google_compute_firewall" "client_allow_external_kdcs" {
  name    = "client-allow-external-kdcs"
  network = "${google_compute_network.client.name}"

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
    "${var.origin_subnet_cidr}"
  ]
}

resource "google_compute_firewall" "client_allow_ssh" {
  name    = "client-allow-ssh"
  network = "${google_compute_network.client.name}"

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

resource "google_compute_firewall" "client_allow_internal" {
  name    = "client-allow-internal"
  network = "${google_compute_network.client.name}"

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
    "${var.client_subnet_cidr}"
  ]
}

// NAT gateway -----------------------------------------------

resource "google_compute_router" "client" {
  name    = "client"
  network = "${google_compute_network.client.self_link}"
}

resource "google_compute_router_nat" "client_nat" {
  name                               = "client"
  router                             = "${google_compute_router.client.name}"
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}


// Dataproc config -----------------------------------------------

resource "google_project_iam_custom_role" "minimalWorker" {
  role_id = "dataproc.minimalWorker"
  title = "Dataproc Minimal Worker"
  description = "Same as Dataproc Worker minus the storage admin perms"
  permissions = [
    "dataproc.agents.create",
    "dataproc.agents.delete",
    "dataproc.agents.get",
    "dataproc.agents.update",
    "dataproc.tasks.lease",
    "dataproc.tasks.listInvalidatedLeases",
    "dataproc.tasks.reportStatus"
  ]
}

resource "google_storage_bucket" "staging_bucket" {
  name = "${var.gcp_project}-staging"
  depends_on = ["google_project_service.service_compute"]  # Dependency required: https://github.com/terraform-providers/terraform-provider-google/issues/1089
  force_destroy = true
}

resource "google_storage_bucket_iam_member" "staging_bucket_perms" {
  bucket = "${google_storage_bucket.staging_bucket.name}"
  role = "roles/storage.admin"
  member = "serviceAccount:${google_service_account.dataproc.email}"
}

resource "google_project_iam_member" "dataproc_minimalWorker" {
  role    = "projects/${var.gcp_project}/roles/dataproc.minimalWorker"
  member  = "serviceAccount:${google_service_account.dataproc.email}"
}

resource "google_storage_bucket" "secrets_bucket" {
  name = "${var.gcp_project}-secrets"
  depends_on = ["google_project_service.service_compute"]  # Dependency required: https://github.com/terraform-providers/terraform-provider-google/issues/1089
  force_destroy = true
}

resource "google_storage_bucket_iam_member" "secrets_bucket_perms" {
  bucket = "${google_storage_bucket.secrets_bucket.name}"
  role        = "roles/storage.objectViewer"
  member      = "serviceAccount:${google_service_account.dataproc.email}"
}

resource "google_kms_key_ring" "dataproc_ring" {
  name     = "dataproc-key-ring"
  location = "${var.gcp_region}"
  depends_on = ["google_project_service.service_kms"]
}

resource "google_kms_crypto_key" "dataproc_key" {
  name            = "dataproc-key"
  key_ring        = "${google_kms_key_ring.dataproc_ring.self_link}"
}

resource "google_kms_crypto_key_iam_binding" "crypto_key" {
  crypto_key_id = "${var.gcp_project}/${var.gcp_region}/${google_kms_key_ring.dataproc_ring.name}/${google_kms_crypto_key.dataproc_key.name}"
  role          = "roles/cloudkms.cryptoKeyDecrypter"
  members = [
    "serviceAccount:${google_service_account.dataproc.email}"
  ]
}

resource "null_resource" "upload_dataproc_kms_keys" {
  provisioner "local-exec" {
    command = <<EOT
        echo '${var.dataproc_root_password}' | \
        gcloud kms encrypt \
            --project=${var.gcp_project} \
            --location=${var.gcp_region} \
            --keyring=${google_kms_key_ring.dataproc_ring.name} \
            --key=${google_kms_crypto_key.dataproc_key.name} \
            --plaintext-file=- \
            --ciphertext-file=root-password.encrypted \
            && gsutil cp root-password.encrypted gs://${google_storage_bucket.secrets_bucket.name} \
            && rm root-password.encrypted

        echo '${var.cross_realm_password}' | \
        gcloud kms encrypt \
            --project=${var.gcp_project} \
            --location=${var.gcp_region} \
            --keyring=${google_kms_key_ring.dataproc_ring.name} \
            --key=${google_kms_crypto_key.dataproc_key.name} \
            --plaintext-file=- \
            --ciphertext-file=shared-password.encrypted \
            && gsutil cp shared-password.encrypted gs://${google_storage_bucket.secrets_bucket.name} \
            && rm shared-password.encrypted
        EOT
  }
  depends_on = [
    "google_kms_crypto_key_iam_binding.crypto_key"
  ]
}


// Kerberos configuration for Dataproc

resource "local_file" "kerberos_config" {
  depends_on = ["null_resource.create_deploy_directory"]
  content = <<EOT
#############################################################################
# This file was automatically generated by Terraform. Do not edit manually. #
#############################################################################
root_principal_password_uri: gs://${google_storage_bucket.secrets_bucket.name}/root-password.encrypted
kms_key_uri: projects/${var.gcp_project}/locations/${var.gcp_region}/keyRings/dataproc-key-ring/cryptoKeys/dataproc-key
cross_realm_trust:
  kdc: "${google_compute_instance.origin_kdc.network_interface.0.network_ip}"
  realm: ${var.origin_realm}
  shared_password_uri: gs://${google_storage_bucket.secrets_bucket.name}/shared-password.encrypted
EOT
  filename = "../deploy/${var.gcp_project}/kerberos-config.yaml"
}