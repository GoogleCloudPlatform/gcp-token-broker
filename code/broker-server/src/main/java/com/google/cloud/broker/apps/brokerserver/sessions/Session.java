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

package com.google.cloud.broker.apps.brokerserver.sessions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.TimeUtils;
import java.util.HashMap;
import java.util.Map;

public class Session extends Model {

  private String id; // UUID
  private String owner; // Identity who owns the session (e.g. alice@EXAMPLE.COM)
  private String renewer; // Identity who is allowed to renew/cancel the session (e.g. yarn@FOO.BAR)
  private String
      target; // Target resource on GCP (e.g. //storage.googleapis.com/projects/_/buckets/example)
  private String scopes; // API scopes for the target resource (e.g.
  // https://www.googleapis.com/auth/devstorage.read_write)
  private Long expiresAt; // Time when the session will expire (in milliseconds)
  private Long creationTime; // Time when the session was created (in milliseconds)

  public Session(
      @JsonProperty("id") String id,
      @JsonProperty("owner") String owner,
      @JsonProperty("renewer") String renewer,
      @JsonProperty("target") String target,
      @JsonProperty("scopes") String scopes,
      @JsonProperty("expiresAt") Long expiresAt,
      @JsonProperty("creationTime") Long creationTime) {
    setId(id);
    setOwner(owner);
    setRenewer(renewer);
    setTarget(target);
    setScopes(scopes);
    setExpiresAt(expiresAt);
    setCreationTime(
        (creationTime == null) ? Long.valueOf(TimeUtils.currentTimeMillis()) : creationTime);
    if (expiresAt == null) {
      extendLifetime();
    }
  }

  public void setDBId(String id) {
    setId(id);
  }

  public String getDBId() {
    return getId();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<String, Object>();
    map.put("id", id);
    map.put("owner", owner);
    map.put("renewer", renewer);
    map.put("target", target);
    map.put("scopes", scopes);
    map.put("expiresAt", expiresAt);
    map.put("creationTime", creationTime);
    return map;
  }

  public static Model fromMap(Map<String, Object> map) {
    return new Session(
        (String) map.get("id"),
        (String) map.get("owner"),
        (String) map.get("renewer"),
        (String) map.get("target"),
        (String) map.get("scopes"),
        (Long) map.get("expiresAt"),
        (Long) map.get("creationTime"));
  }

  public void extendLifetime() {
    long now = TimeUtils.currentTimeMillis();
    long sessionMaximumLifetime =
        AppSettings.getInstance().getLong(AppSettings.SESSION_MAXIMUM_LIFETIME);
    long sessionRenewPeriod = AppSettings.getInstance().getLong(AppSettings.SESSION_RENEW_PERIOD);
    expiresAt = Math.min(now + sessionRenewPeriod, creationTime + sessionMaximumLifetime);
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

  public String getScopes() {
    return scopes;
  }

  public void setScopes(String scopes) {
    this.scopes = scopes;
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
