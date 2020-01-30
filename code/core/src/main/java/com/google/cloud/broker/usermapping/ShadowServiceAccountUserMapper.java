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

package com.google.cloud.broker.usermapping;

import com.google.cloud.broker.settings.AppSettings;

public class ShadowServiceAccountUserMapper extends AbstractUserMapper {
    @Override
    public String map(String name) {
        String shadowProject = AppSettings.getInstance().getString(AppSettings.SHADOW_PROJECT);
        String shadowPattern = AppSettings.getInstance().getString(AppSettings.SHADOW_USERNAME_PATTERN);
        String username;
        try {
            username = name.split("@")[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException();
        }
        if (username.length() == 0) {
            throw new IllegalArgumentException();
        }
        return String.format(shadowPattern, username) + "@" + shadowProject + ".iam.gserviceaccount.com";
    }
}
