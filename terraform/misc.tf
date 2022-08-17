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

// Google APIs --------------------------------------------------------------

resource "google_project_service" "service_compute" {
  service = "compute.googleapis.com"
}

resource "google_project_service" "service_iamcredentials" {
  service = "iam.googleapis.com"
}

resource "google_project_service" "service_dataproc" {
  service = "dataproc.googleapis.com"
}

resource "google_project_service" "service_kms" {
  service = "cloudkms.googleapis.com"
}

resource "google_project_service" "service_redis" {
  service = "redis.googleapis.com"
}

resource "google_project_service" "service_containerregistry" {
  service = "containerregistry.googleapis.com"
}

resource "google_project_service" "service_artifactregistry" {
  service = "artifactregistry.googleapis.com"
}

// Used for proxy user support to check group membership
resource "google_project_service" "service_admin" {
  service = "admin.googleapis.com"
}

resource "google_project_service" "service_secretmanager" {
  service = "secretmanager.googleapis.com"
}

resource "google_project_service" "service_run" {
  service = "run.googleapis.com"
}

// Test bucket -----------------------------------------------------------------------

resource "google_storage_bucket" "demo_bucket" {
  name          = "${var.gcp_project}-demo-bucket"
  depends_on    = [google_project_service.service_compute] # Dependency required: https://github.com/terraform-providers/terraform-provider-google/issues/1089
  force_destroy = true
  location      = var.gcp_region
}

resource "google_storage_bucket_iam_member" "demo_bucket_perms_user" {
  bucket = google_storage_bucket.demo_bucket.name
  role   = "roles/storage.admin"
  member = "user:${element(var.test_users, 0)}@${var.gsuite_domain}"
}

resource "google_storage_bucket_iam_member" "demo_bucket_perms_svcacct" {
  bucket = google_storage_bucket.demo_bucket.name
  role   = "roles/storage.admin"
  member = "serviceAccount:${google_service_account.test_user_serviceaccount[0].email}"
}

resource "null_resource" "upload_demo_parquet_file" {
  provisioner "local-exec" {
    command = <<EOT
        gsutil cp \
            gs://hive-solution/part-00000.parquet \
            gs://${google_storage_bucket.demo_bucket.name}/datasets/transactions/part-00000.parquet
    
EOT

  }
  depends_on = [google_storage_bucket.demo_bucket]
}

resource "null_resource" "upload_demo_macbeth_file" {
  provisioner "local-exec" {
    command = <<EOT
        gsutil cp \
            gs://apache-beam-samples/shakespeare/macbeth.txt \
            gs://${google_storage_bucket.demo_bucket.name}/datasets/shakespeare/macbeth.txt

EOT

  }
  depends_on = [google_storage_bucket.demo_bucket]
}