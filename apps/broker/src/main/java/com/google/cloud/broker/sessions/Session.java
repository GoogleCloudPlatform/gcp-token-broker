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

import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.TimeUtils;



public class Session extends Model {

    private String id;          // UUID
    private String owner;       // Identity who owns the session (e.g. alice@EXAMPLE.COM)
    private String renewer;     // Identity who is allowed to renew/cancel the session (e.g. yarn@FOO.BAR)
    private String target;      // Target resource on GCP (e.g. gs://example)
    private String scope;       // API scope for the target resource (e.g. https://www.googleapis.com/auth/devstorage.read_write)
    private String password;    // Randomly generated password for the session
    private Long expiresAt;     // Time when the session will expire (in milliseconds)
    private Long creationTime;  // Time when the session was created (in milliseconds)

    public Session(@JsonProperty("id") String id,
                   @JsonProperty("owner") String owner,
                   @JsonProperty("renewer") String renewer,
                   @JsonProperty("target") String target,
                   @JsonProperty("scope") String scope,
                   @JsonProperty("password") String password,
                   @JsonProperty("expiresAt") Long expiresAt,
                   @JsonProperty("creationTime") Long creationTime) {
        this.id = id;
        this.owner = owner;
        this.renewer = renewer;
        this.target = target;
        this.scope = scope;
        this.expiresAt = expiresAt;
        this.creationTime = (creationTime==null) ? Long.valueOf(TimeUtils.currentTimeMillis()) : creationTime;
        this.password = (password==null) ? generateRandomPassword() : password;
        if (expiresAt==null) {
            extendLifetime();
        }
    }

    public void setDBId(String id) {
        setId(id);
    }

    public String getDBId() {
        return getId();
    }

    public HashMap<String, Object> toHashMap() {
        HashMap<String, Object> hashmap = new HashMap<String, Object>();
        hashmap.put("id", id);
        hashmap.put("owner", owner);
        hashmap.put("renewer", renewer);
        hashmap.put("target", target);
        hashmap.put("scope", scope);
        hashmap.put("password", password);
        hashmap.put("expiresAt", expiresAt);
        hashmap.put("creationTime", creationTime);
        return hashmap;
    }

    public static Model fromHashMap(HashMap<String, Object> hashmap) {
        return new Session(
            (String) hashmap.get("id"),
            (String) hashmap.get("owner"),
            (String) hashmap.get("renewer"),
            (String) hashmap.get("target"),
            (String) hashmap.get("scope"),
            (String) hashmap.get("password"),
            (Long) hashmap.get("expiresAt"),
            (Long) hashmap.get("creationTime")
        );
    }

    private static String generateRandomPassword() {
        Random random = new SecureRandom();
        int length = 24;
        String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    public void extendLifetime() {
        long now = TimeUtils.currentTimeMillis();
        expiresAt = Math.min(
            now + Long.parseLong(AppSettings.requireProperty("SESSION_RENEW_PERIOD")),
            creationTime + Long.parseLong(AppSettings.requireProperty("SESSION_MAXIMUM_LIFETIME"))
        );
    }

    @JsonIgnore
    public boolean isExpired() {
        long now = TimeUtils.currentTimeMillis();
        return (now >= expiresAt);
    }



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRenewer() {
        return renewer;
    }

    public void setRenewer(String renewer) {
        this.renewer = renewer;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Long creationTime) {
        this.creationTime = creationTime;
    }

}