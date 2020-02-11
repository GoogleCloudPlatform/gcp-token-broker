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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.grpc.Status;

import com.google.cloud.broker.settings.AppSettings;

public class Validation {

    public static void validateParameterIsEmpty(String parameter, String value) {
        if (value.length() > 0) {
            throw Status.INVALID_ARGUMENT
                .withDescription(String.format("Request's parameter `%s` must be empty", parameter))
                .asRuntimeException();
        }
    }

    public static void validateParameterIsEmpty(String parameter, List<String> values) {
        if (values.size() > 0) {
            throw Status.INVALID_ARGUMENT
                .withDescription(String.format("Request's parameter `%s` must be empty", parameter))
                .asRuntimeException();
        }
    }

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

    public static void validateEmail(String email) {
        Pattern parser = Pattern.compile("([a-zA-Z0-9.-]+)@([a-zA-Z0-9.-]+)");
        Matcher match = parser.matcher(email);
        if (!match.matches()) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
    }

}
