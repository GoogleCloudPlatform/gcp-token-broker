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


# Create the peerings in sequence, as you can't set up multiple peerings
# with the same VPC at the same time. See https://github.com/terraform-providers/terraform-provider-google/issues/3034

// Origin <--> Broker ---------------------------------------------------------------

resource "google_compute_network_peering" "origin_broker_peering1" {
  name = "origin-broker-peering1"
  network = "${google_compute_network.origin.self_link}"
  peer_network = "${google_compute_network.broker.self_link}"
  depends_on = [
    "google_compute_subnetwork.broker_cluster_subnet",
    "google_compute_subnetwork.origin_subnet",
  ]
}

resource "google_compute_network_peering" "origin_broker_peering2" {
  name = "origin-broker-peering2"
  network = "${google_compute_network.broker.self_link}"
  peer_network = "${google_compute_network.origin.self_link}"
  depends_on = [
    "google_compute_network_peering.origin_broker_peering1",
  ]
}


// Client <--> Broker ---------------------------------------------------------------

resource "google_compute_network_peering" "client_broker_peering1" {
  name = "client-broker-peering1"
  network = "${google_compute_network.client.self_link}"
  peer_network = "${google_compute_network.broker.self_link}"
  depends_on = [
    "google_compute_subnetwork.broker_cluster_subnet",
    "google_compute_subnetwork.client_subnet",
    "google_compute_network_peering.origin_broker_peering2",
  ]
}

resource "google_compute_network_peering" "client_broker_peering2" {
  name = "client-broker-peering2"
  network = "${google_compute_network.broker.self_link}"
  peer_network = "${google_compute_network.client.self_link}"
  depends_on = [
    "google_compute_network_peering.client_broker_peering1",
  ]
}


// Client <--> Origin ---------------------------------------------------------------

resource "google_compute_network_peering" "client_origin_peering1" {
  name = "client-origin-peering1"
  network = "${google_compute_network.client.self_link}"
  peer_network = "${google_compute_network.origin.self_link}"
  depends_on = [
    "google_compute_subnetwork.origin_subnet",
    "google_compute_subnetwork.client_subnet",
    "google_compute_network_peering.client_broker_peering2",
  ]
}

resource "google_compute_network_peering" "client_origin_peering2" {
  name = "client-origin-peering2"
  network = "${google_compute_network.origin.self_link}"
  peer_network = "${google_compute_network.client.self_link}"
  depends_on = [
    "google_compute_network_peering.client_origin_peering1",
  ]
}