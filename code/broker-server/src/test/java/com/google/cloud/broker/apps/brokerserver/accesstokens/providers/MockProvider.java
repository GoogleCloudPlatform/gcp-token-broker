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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import java.util.List;

/** Mock provider only used for testing. Do NOT use in production! */
public class MockProvider extends AbstractUserProvider {

  @Override
  public AccessToken getAccessToken(String googleIdentity, List<String> scopes) {
    return new AccessToken(
        "FakeAccessToken/GoogleIdentity=" + googleIdentity + ";Scopes=" + String.join(",", scopes),
        999999999L);
  }
}
