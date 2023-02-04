// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.client.connect;

public class BrokerServerInfo {

  private String serverUri;
  private String kerberosPrincipal;
  private String certificate;
  private String certificatePath;

  public BrokerServerInfo(
      String serverUri, String kerberosPrincipal, String certificate, String certificatePath) {
    this.serverUri = serverUri;
    this.kerberosPrincipal = kerberosPrincipal;
    this.certificate = certificate;
    this.certificatePath = certificatePath;
  }

  public String getServerUri() {
    return serverUri;
  }

  public void setServerUri(String serverUri) {
    this.serverUri = serverUri;
  }

  public String getKerberosPrincipal() {
    return kerberosPrincipal;
  }

  public void setKerberosPrincipal(String kerberosPrincipal) {
    this.kerberosPrincipal = kerberosPrincipal;
  }

  public String getCertificate() {
    return certificate;
  }

  public void setCertificate(String certificate) {
    this.certificate = certificate;
  }

  public String getCertificatePath() {
    return certificatePath;
  }

  public void setCertificatePath(String certificatePath) {
    this.certificatePath = certificatePath;
  }
}
