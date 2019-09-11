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

package com.google.cloud.broker.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.utils.TimeUtils;

import java.util.HashMap;

public class RefreshToken extends Model {

    private String id;  // GSuite email address (e.g. alice@example.com)
    byte[] value;       // The actual OAuth refresh token (Recommendation: encrypt this value)
    Long creationTime;  // The time when the object was created (in milliseconds)

    public RefreshToken(@JsonProperty("id") String id,
                        @JsonProperty("value") byte[] value,
                        @JsonProperty("creationTime") Long creationTime) {
        this.id = id;
        this.value = value;
        this.creationTime = (creationTime==null) ? Long.valueOf(TimeUtils.currentTimeMillis()) : creationTime;
    }

    @Override
    public HashMap<String, Object> toHashMap() {
        HashMap<String, Object> hashmap = new HashMap<String, Object>();
        hashmap.put("id", id);
        hashmap.put("value", value);
        hashmap.put("creationTime", creationTime);
        return hashmap;
    }

    public static Model fromHashMap(HashMap<String, Object> hashmap) {
        return new RefreshToken(
            (String) hashmap.get("id"),
            (byte[]) hashmap.get("value"),
            (Long) hashmap.get("creationTime")
        );
    }

    public void setDBId(String id) {
        setId(id);
    }

    public String getDBId() {
        return getId();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public Long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Long creationTime) {
        this.creationTime = creationTime;
    }
}
