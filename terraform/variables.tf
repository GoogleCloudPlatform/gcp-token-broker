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

variable "gcp_project" {
}

variable "gcp_region" {
  default = "us-west1"
}

variable "gcp_zone" {
  default = "us-west1-a"
}

variable "datastore_region" {
  default = "us-west2"
}

variable "origin_realm" {
}

variable "gsuite_domain" {
}

// Broker --------------------------------------

variable "broker_host" {
  default = "10.2.1.255.xip.io"
}

variable "broker_service_ip" {
  default = "10.2.1.255"
}

variable "broker_subnet_cidr" {
  default = "10.2.0.0/16"
}

variable "broker_service_port" {
  default = 443
}

variable "broker_initial_node_count" {
  default = 4
}

variable "broker_master_cidr" {
  default = "10.3.0.0/28"
}

variable "broker_master_authorized_cidr" {
  default = "0.0.0.0/0"
}

variable "broker_pod_cidr" {
  default = "10.4.0.0/14"
}

variable "broker_service_cidr" {
  default = "10.8.0.0/20"
}

// Authorizer --------------------------------------

variable "authorizer_host" {
}

// Origin KDC --------------------------------------

variable "origin_subnet_cidr" {
  default = "10.11.0.0/29"
}

variable "origin_kdc_ip" {
  default = "10.11.0.3"
}

// Client ----------------------------------------

variable "client_subnet_cidr" {
  default = "10.21.0.0/16"
}

variable "dataproc_root_password" {
  default = "change-me"
}

// Cross-realm trust -----------------------------

variable "cross_realm_password" {
  default = "change-me"
}

locals {
  dataproc_realm = "${upper(var.gcp_zone)}.C.${upper(var.gcp_project)}.INTERNAL"
}

// Test users ------------------------------------

variable "test_users" {
  type    = list(string)
  default = ["alice", "bob", "john"]
}

