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

package com.google.cloud.broker.providers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AccessToken {

    private String value;
    private long expiresAt;
    
    public AccessToken(String value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public String getValue() {
        return value;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String toJSON() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("value", value);
        jsonObject.addProperty("expires_at", expiresAt);
        return new Gson().toJson(jsonObject);
    }

    public static AccessToken fromJSON(String json) {
        try {
            JsonObject jsonObject = (JsonObject) new JsonParser().parse(json);
            String value = jsonObject.get("value").getAsString();
            long expiresAt = jsonObject.get("expires_at").getAsLong();
            return new AccessToken(value, expiresAt);
        }
        catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}
