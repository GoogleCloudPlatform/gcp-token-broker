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

import com.google.cloud.broker.utils.TimeUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.broker.database.models.Model;


import java.util.HashMap;
import java.util.Map;

public class RefreshToken extends Model {

    private String id;          // GSuite email address (e.g. alice@example.com)
    private byte[] value;       // The actual OAuth refresh token (Recommendation: encrypt this value)
    private Long creationTime;  // The time when the object was created (in milliseconds)

    public RefreshToken(@JsonProperty("id") String id,
                        @JsonProperty("value") byte[] value,
                        @JsonProperty("creationTime") Long creationTime) {
        setId(id);
        setValue(value);
        setCreationTime(
            (creationTime==null) ? Long.valueOf(TimeUtils.currentTimeMillis()) : creationTime
        );
    }

    @Override
    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("id", id);
        map.put("value", value);
        map.put("creationTime", creationTime);
        return map;
    }

    public static Model fromMap(Map<String, Object> map) {
        return new RefreshToken(
            (String) map.get("id"),
            (byte[]) map.get("value"),
            (Long) map.get("creationTime")
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
