// Copyright 2019 Google LLC
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

import java.util.*;

import io.grpc.Status;

import com.google.cloud.broker.settings.AppSettings;

public class Validation {

    public static void validateParameterNotEmpty(String parameter, String value) {
        if (value.length() == 0) {
            throw Status.INVALID_ARGUMENT
                .withDescription(String.format("Request must provide `%s`", parameter))
                .asRuntimeException();
        }
    }

    public static void validateParameterNotEmpty(String parameter, List<String> values) {
        if (values.size() == 0) {
            throw Status.INVALID_ARGUMENT
                .withDescription(String.format("Request must provide `%s`", parameter))
                .asRuntimeException();
        }
    }

    public static void validateImpersonator(String impersonator, String impersonated) {
        if (impersonator.equals(impersonated)) {
            // A user is allowed to impersonate themselves
            return;
        }
        else {
            String proxyString = AppSettings.getInstance().getString(AppSettings.PROXY_USER_WHITELIST);
            String[] proxyUsers = proxyString.split("\\s*,\\s*");
            boolean whitelisted = Arrays.stream(proxyUsers).anyMatch(impersonator::equals);
            if (!whitelisted) {
                throw Status.PERMISSION_DENIED
                    .withDescription(String.format("%s is not a whitelisted impersonator", impersonator))
                    .asRuntimeException();
            }
        }
    }

    public static void validateScopes(List<String> scopes) {
        List<String> whitelist = AppSettings.getInstance().getStringList(AppSettings.SCOPES_WHITELIST);
        Set<String> scopeSet = new HashSet<String>(scopes);
        Set<String> whitelistSet = new HashSet<String>(whitelist);
        if (!whitelistSet.containsAll(scopeSet)) {
            throw Status.PERMISSION_DENIED
                .withDescription(String.format("`[%s]` are not whitelisted scopes", String.join(",", scopes)))
                .asRuntimeException();
        }
    }

}
