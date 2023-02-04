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

package com.google.cloud.broker.oauth;

import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefreshTokenUtils {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static class ErrorResponse {
    public String error;
    public String error_description;
  }

  /** Calls the Google OAuth API to revoke the provided refresh token. */
  public static void revoke(RefreshToken token) {
    String decryptedValue =
        new String(AbstractEncryptionBackend.getInstance().decrypt(token.getValue()));
    List<NameValuePair> params = Lists.newArrayList();
    params.add(new BasicNameValuePair("token", decryptedValue));
    HttpPost request = new HttpPost("https://oauth2.googleapis.com/revoke");
    try {
      request.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    CloseableHttpClient httpclient = HttpClients.createDefault();
    CloseableHttpResponse response;
    try {
      response = httpclient.execute(request);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == 200) {
      // Token successfully revoked
      logger.info("Revoked refresh token: " + token.getId());
    } else {
      Gson gson = new Gson();
      String responseString;
      try {
        responseString = EntityUtils.toString(response.getEntity());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      ErrorResponse errorResponse = gson.fromJson(responseString, ErrorResponse.class);
      if (statusCode == 400
          && errorResponse.error != null
          && errorResponse.error.equals("invalid_token")) {
        // Token is invalid (e.g. it's already revoked)
        logger.info("Refresh token already revoked: " + token.getId());
      } else {
        // Unhandled error
        throw new RuntimeException(
            String.format(
                "Error while revoking refresh token. Response (status code %s):\n%s",
                statusCode, responseString));
      }
    }
  }
}
