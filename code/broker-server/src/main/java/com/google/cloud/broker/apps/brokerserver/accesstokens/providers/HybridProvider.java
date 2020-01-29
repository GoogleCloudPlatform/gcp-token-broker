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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.util.List;

import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.InstanceUtils;

public class HybridProvider extends AbstractProvider {

    AbstractUserProvider userProvider;
    ServiceAccountProvider serviceAccountProvider;

    public HybridProvider() {
        String className = AppSettings.getInstance().getString(AppSettings.HYBRID_USER_PROVIDER);
        userProvider = (AbstractUserProvider) InstanceUtils.invokeConstructor(className);
        serviceAccountProvider = new ServiceAccountProvider();
    }

    @Override
    public AccessToken getAccessToken(String googleIdentity, List<String> scopes) {
        if (googleIdentity.endsWith(".iam.gserviceaccount.com")) {
            return serviceAccountProvider.getAccessToken(googleIdentity, scopes);
        }
        else {
            return userProvider.getAccessToken(googleIdentity, scopes);
        }
    }

}
