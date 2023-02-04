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

package com.google.cloud.broker.apps.brokerserver.validation;

import com.google.cloud.broker.settings.AppSettings;
import io.grpc.Status;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScopeValidation {

  public static void validateScopes(List<String> scopes) {
    List<String> allowlist = AppSettings.getInstance().getStringList(AppSettings.SCOPES_ALLOWLIST);
    Set<String> scopeSet = new HashSet<String>(scopes);
    Set<String> allowlistSet = new HashSet<String>(allowlist);
    if (!allowlistSet.containsAll(scopeSet)) {
      throw Status.PERMISSION_DENIED
          .withDescription(
              String.format("`[%s]` are not allowlisted scopes", String.join(",", scopes)))
          .asRuntimeException();
    }
  }
}
