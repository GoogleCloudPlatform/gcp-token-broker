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

resource "google_service_account" "test_user_serviceaccount" {
  count = "${length(var.test_users)}-svc-acct"
  account_id = "${element(var.test_users, count.index)}"
  display_name = "${element(var.test_users, count.index)}'s service account"
}

resource "google_service_account_iam_member" "token_creator_0" {
  service_account_id = "${google_service_account.test_user_serviceaccount.0.name}"
  role        = "roles/iam.serviceAccountTokenCreator"
  member      = "serviceAccount:${google_service_account.broker.email}"
}

resource "google_service_account_iam_member" "token_creator_1" {
  service_account_id = "${google_service_account.test_user_serviceaccount.1.name}"
  role        = "roles/iam.serviceAccountTokenCreator"
  member      = "serviceAccount:${google_service_account.broker.email}"
}