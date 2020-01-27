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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.model.SignJwtRequest;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;
import com.google.cloud.broker.utils.Constants;
import com.google.cloud.broker.utils.TimeUtils;
import io.grpc.Status;


public class DomainWideDelegationAuthorityProvider extends AbstractProvider {

    private final static String IAM_API = "https://www.googleapis.com/auth/iam";

    private String getSignedJWT(String googleIdentity, List<String> scopes) {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault().createScoped(IAM_API);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String serviceAccount;
        if (credentials instanceof ServiceAccountCredentials) {
            serviceAccount = ((ServiceAccountCredentials) credentials).getAccount();
        }
        else if (credentials instanceof ComputeEngineCredentials) {
            serviceAccount = ((ComputeEngineCredentials) credentials).getAccount();
        }
        else {
            throw new RuntimeException("Invalid credentials");
        }

        // Create the JWT payload
        long jwtLifetime = 30;
        long iat = TimeUtils.currentTimeMillis() / 1000L;
        long exp = iat + jwtLifetime;
        HashMap<String, Object> jwtPayload = new HashMap<>();
        jwtPayload.put("scope", String.join(",", scopes));
        jwtPayload.put("aud", "https://www.googleapis.com/oauth2/v4/token");
        jwtPayload.put("iat", iat);
        jwtPayload.put("exp", exp);
        jwtPayload.put("sub", googleIdentity);
        jwtPayload.put("iss", serviceAccount);

        try {
            // Create the SignJWT request body
            SignJwtRequest requestBody = new SignJwtRequest();
            requestBody.setPayload(new JacksonFactory().toString(jwtPayload));

            // Create the SignJWT request
            credentials.refresh();
            Credential bearerToken = new Credential(
                BearerToken.authorizationHeaderAccessMethod()).setAccessToken(credentials.getAccessToken().getTokenValue());
            Iam iamService = new Iam.Builder(Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(), bearerToken)
                .setApplicationName(Constants.APPLICATION_NAME).build();
            String name = String.format("projects/-/serviceAccounts/%s", serviceAccount);
            Iam.Projects.ServiceAccounts.SignJwt request =
                iamService.projects().serviceAccounts().signJwt(name, requestBody);

            // Execute the request
            return request.execute().getSignedJwt();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403) {
                throw Status.PERMISSION_DENIED.asRuntimeException();
            }
            else {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AccessToken tradeSignedJWTForAccessToken(String signedJWT) {
        try {
            TokenRequest request = new TokenRequest(
                Utils.getDefaultTransport(),
                Utils.getDefaultJsonFactory(),
                new GenericUrl("https://www.googleapis.com/oauth2/v4/token"),
                "assertion");
            request.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            request.put("assertion", signedJWT);
            TokenResponse response = request.execute();
            return new AccessToken(
                response.getAccessToken(),
                TimeUtils.currentTimeMillis() + response.getExpiresInSeconds() * 1000);
        } catch (TokenResponseException e) {
            throw Status.PERMISSION_DENIED.asRuntimeException();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AccessToken getAccessToken(String googleIdentity, List<String> scopes) {
        // Get signed JWT
        String signedJWT = getSignedJWT(googleIdentity, scopes);
        // Obtain and return new access token for the owner
        return tradeSignedJWTForAccessToken(signedJWT);
    }

}
