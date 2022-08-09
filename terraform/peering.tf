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


// Client <--> Origin ---------------------------------------------------------------

resource "google_compute_network_peering" "client_origin_peering1" {
  name         = "client-origin-peering1"
  network      = google_compute_network.client.self_link
  peer_network = google_compute_network.origin.self_link
  depends_on = [
    google_compute_subnetwork.origin_subnet,
    google_compute_subnetwork.client_subnet,
  ]
}

resource "google_compute_network_peering" "client_origin_peering2" {
  name         = "client-origin-peering2"
  network      = google_compute_network.origin.self_link
  peer_network = google_compute_network.client.self_link
}
