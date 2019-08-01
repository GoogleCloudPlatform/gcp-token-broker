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

package com.google.cloud.broker.sessions;

import java.security.SecureRandom;
import java.util.Random;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.database.models.CreationTimeModel;
import com.google.cloud.broker.utils.TimeUtils;



public class Session extends CreationTimeModel {

    @JsonCreator
    public Session(@JsonProperty("values") HashMap<String, Object> values) {
        super(values);
        if (!this.values.containsKey("password")) {
            generateRandomPassword();
        }
        if (!this.values.containsKey("expires_at")) {
            extendLifetime();
        }
    }

    protected void generateRandomPassword() {
        Random random = new SecureRandom();
        int length = 16;
        String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(characters.charAt(random.nextInt(characters.length())));
        }
        values.put("password", password.toString());
    }

    public void extendLifetime() {
        AppSettings settings = AppSettings.getInstance();
        long now = TimeUtils.currentTimeMillis();
        long creationTime = (long) getValue("creation_time");
        values.put("expires_at", Math.min(
            now + Long.parseLong(settings.getProperty("SESSION_RENEW_PERIOD")),
            creationTime + Long.parseLong(settings.getProperty("SESSION_MAXIMUM_LIFETIME"))
        ));
    }

    @JsonIgnore
    public boolean isExpired() {
        long now = TimeUtils.currentTimeMillis();
        long expiresAt = (long) values.get("expires_at");
        return (now >= expiresAt);
    }

}