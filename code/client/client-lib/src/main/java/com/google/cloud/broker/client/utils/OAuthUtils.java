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

package com.google.cloud.broker.client.utils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;

public class OAuthUtils {

  public static GoogleCredentials getApplicationDefaultCredentials() {
    GoogleCredentials credentials;
    try {
      credentials = GoogleCredentials.getApplicationDefault();
      if (!(credentials instanceof IdTokenProvider)) {
        throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider.");
      }
      return credentials;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static com.google.auth.oauth2.AccessToken getApplicationDefaultIdToken(String audience) {
    try {
      GoogleCredentials credentials = getApplicationDefaultCredentials();
      if (!(credentials instanceof IdTokenProvider)) {
        throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider.");
      }
      IdTokenCredentials tokenCredential =
          IdTokenCredentials.newBuilder()
              .setIdTokenProvider((IdTokenProvider) credentials)
              .setTargetAudience(audience)
              .build();
      return tokenCredential.refreshAccessToken();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
