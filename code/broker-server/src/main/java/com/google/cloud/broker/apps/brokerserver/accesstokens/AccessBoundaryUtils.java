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

package com.google.cloud.broker.apps.brokerserver.accesstokens;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.grpc.Status;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.cloud.broker.settings.AppSettings;


public class AccessBoundaryUtils {

    private static class BoundedAccessTokenResponse {
        public String access_token;
        public long expires_in;
    }

    public static AccessToken addAccessBoundary(AccessToken accessToken, String target) {
        // Retrieve the access boundary permissions from configuration
        JsonArray permissionsJSON = new JsonArray();
        List<String> permissions = AppSettings.getInstance().getStringList(AppSettings.ACCESS_TOKEN_BOUNDARY_PERMISSIONS);
        for (String permission: permissions) {
            permissionsJSON.add(permission);
        }

        // Create the access boundary spec
        Gson gson = new Gson();
        JsonObject accessBoundaryRule = new JsonObject();
        accessBoundaryRule.addProperty("availableResource", target);
        accessBoundaryRule.add("availablePermissions", permissionsJSON);
        JsonArray accessBoundaryRules = new JsonArray();
        accessBoundaryRules.add(accessBoundaryRule);
        JsonObject accessBoundary = new JsonObject();
        accessBoundary.add("accessBoundaryRules", accessBoundaryRules);
        String encodedAccessBoundary;
        try {
            encodedAccessBoundary = URLEncoder.encode(gson.toJson(accessBoundary), StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        // Initialize the HTTP request
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://securetoken.googleapis.com/v1alpha2/identitybindingtoken");
        httpPost.setHeader("Content-type", "application/json");

        // Set the request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        requestBody.put("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        requestBody.put("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        requestBody.put("subject_token", accessToken.getValue());
        requestBody.put("access_boundary", encodedAccessBoundary);
        try {
            httpPost.setEntity(new StringEntity(gson.toJson(requestBody)));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        // Submit the request
        BoundedAccessTokenResponse accessTokenResponse;
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw Status.PERMISSION_DENIED.asRuntimeException();
            }
            accessTokenResponse = gson.fromJson(EntityUtils.toString(response.getEntity()), BoundedAccessTokenResponse.class);
            httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new AccessToken(accessTokenResponse.access_token, accessTokenResponse.expires_in);
    }

}
