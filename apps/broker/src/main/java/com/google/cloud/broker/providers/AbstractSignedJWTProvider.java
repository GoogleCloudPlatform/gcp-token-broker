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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;

import com.google.api.client.auth.oauth2.TokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Clock;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.model.SignJwtRequest;
import com.google.api.services.iam.v1.model.SignJwtResponse;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.cloud.broker.settings.AppSettings;


public abstract class AbstractSignedJWTProvider extends AbstractProvider {

    protected boolean brokerIssuer;

    public AbstractSignedJWTProvider(boolean brokerIssuer) {
        this.brokerIssuer = brokerIssuer;
    }

    public boolean isBrokerIssuer() {
        return brokerIssuer;
    }

    private Iam createIamService(BrokerDetails details) {
        HttpTransport httpTransport;
        JsonFactory jsonFactory;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            jsonFactory = JacksonFactory.getDefaultInstance();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        GoogleCredential credential = new GoogleCredential();
        credential.setAccessToken(details.accessToken);
        return new Iam.Builder(httpTransport, jsonFactory, credential).build();
    }

    class BrokerDetails {
        public String accessToken;
        public String serviceAccount;
    }

    private BrokerDetails getBrokerDetails() {
        BrokerDetails details = new BrokerDetails();
        ComputeEngineCredentials credentials = ComputeEngineCredentials.create();
        details.serviceAccount = credentials.getAccount();
        try {
            details.accessToken = credentials.refreshAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return details;
    }

    private String getSignedJWT(String owner, String scope) {
        // Get broker's service account details
        BrokerDetails details = getBrokerDetails();

        // Create the JWT payload
        AppSettings settings = AppSettings.getInstance();
        long iat = Clock.SYSTEM.currentTimeMillis() / 1000L;
        long exp = iat + Long.parseLong(settings.getProperty("JWT_LIFE"));
        HashMap<String, Object> jwtPayload = new HashMap<>();
        jwtPayload.put("scope", scope);
        jwtPayload.put("aud", "https://www.googleapis.com/oauth2/v4/token");
        jwtPayload.put("iat", iat);
        jwtPayload.put("exp", exp);
        String serviceAccount;
        String googleIdentity = getGoogleIdentify(owner);
        if (isBrokerIssuer()) {
            jwtPayload.put("sub", googleIdentity);
            jwtPayload.put("iss", details.serviceAccount);
            serviceAccount = details.serviceAccount;
        }
        else {
            jwtPayload.put("iss", googleIdentity);
            serviceAccount = googleIdentity;
        }

        // Get a signed JWT
        SignJwtResponse response;
        try {
            // Create the SignJWT request body
            SignJwtRequest requestBody = new SignJwtRequest();
            requestBody.setPayload(new JacksonFactory().toString(jwtPayload));

            // Create the SignJWT request
            Iam iamService = createIamService(details);
            String name = String.format("projects/-/serviceAccounts/%s", serviceAccount);
            Iam.Projects.ServiceAccounts.SignJwt request =
                iamService.projects().serviceAccounts().signJwt(name, requestBody);

            // Execute the request
            response = request.execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(response);
        return response.getSignedJwt();
    }

    protected AccessToken tradeSignedJWTForAccessToken(String signedJWT) {
        HttpTransport httpTransport;
        JsonFactory jsonFactory;
        TokenResponse response;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            jsonFactory = JacksonFactory.getDefaultInstance();
            TokenRequest request = new TokenRequest(
                httpTransport,
                jsonFactory,
                new GenericUrl("https://www.googleapis.com/oauth2/v4/token"),
                "assertion");
            request.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            request.put("assertion", signedJWT);
            response = request.execute();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        return new AccessToken(
            response.getAccessToken(),
            Clock.SYSTEM.currentTimeMillis() + response.getExpiresInSeconds() * 1000);
    }

    @Override
    public AccessToken getAccessToken(String owner, String scope) {
        // Get signed JWT
        String signedJWT = getSignedJWT(owner, scope);

        // Obtain new access token for the owner
        AccessToken accessToken = tradeSignedJWTForAccessToken(signedJWT);

        return accessToken;
    }
}
