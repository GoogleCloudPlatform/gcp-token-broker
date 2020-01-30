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

package com.google.cloud.broker.authentication.backends;

import org.slf4j.MDC;

import com.google.cloud.broker.authentication.AuthorizationHeaderServerInterceptor;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.InstanceUtils;


public abstract class AbstractAuthenticationBackend {

    private static AbstractAuthenticationBackend instance;

    public static AbstractAuthenticationBackend getInstance() {
        String className = AppSettings.getInstance().getString(AppSettings.AUTHENTICATION_BACKEND);
        if (instance == null || !className.equals(instance.getClass().getCanonicalName())) {
            instance = (AbstractAuthenticationBackend) InstanceUtils.invokeConstructor(className);
        }
        return instance;
    }

    public String authenticateUser() {
        String authorizationHeader = AuthorizationHeaderServerInterceptor.AUTHORIZATION_CONTEXT_KEY.get();
        String authenticatedUser = authenticateUser(authorizationHeader);
        MDC.put("authenticated_user", authenticatedUser);
        return authenticatedUser;
    }

    public abstract String authenticateUser(String authorizationHeader);
}