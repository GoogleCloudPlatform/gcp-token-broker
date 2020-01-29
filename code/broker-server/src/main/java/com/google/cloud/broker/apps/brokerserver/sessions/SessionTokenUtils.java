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

package com.google.cloud.broker.apps.brokerserver.sessions;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.grpc.Status;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;


public class SessionTokenUtils {

    private static String TOKEN_SEPARATOR = ".";


    private static SessionToken unmarshallSessionToken(String token) {
        String[] split = token.split(Pattern.quote(TOKEN_SEPARATOR));
        try {
            String headerString = new String(Base64.getUrlDecoder().decode(split[0]));
            JsonObject header = (JsonObject) new JsonParser().parse(headerString);
            String sessionId = header.get("session_id").getAsString();
            byte[] signature = Base64.getUrlDecoder().decode(split[1]);
            return new SessionToken(sessionId, signature);
        }
        catch (ArrayIndexOutOfBoundsException | JsonSyntaxException e) {
            throw Status.UNAUTHENTICATED.withDescription("Session token is invalid").asRuntimeException();
        }
    }


    public static Session getSessionFromRawToken(String rawToken) {
        SessionToken sessionToken = unmarshallSessionToken(rawToken);

        // Fetch session from the database
        Session session;
        try {
            session = (Session) AbstractDatabaseBackend.getInstance().get(Session.class, sessionToken.getSessionId());
        }
        catch (DatabaseObjectNotFound e) {
            throw Status.UNAUTHENTICATED.withDescription("Session token is invalid or has expired").asRuntimeException();
        }

        // Verify that the provided signature is valid
        byte[] signature = sign(session);
        if (MessageDigest.isEqual(signature, sessionToken.getSignature())) {
            return session;
        }
        else {
            throw Status.UNAUTHENTICATED.withDescription("Invalid session token").asRuntimeException();
        }
    }


    public static String marshallSessionToken(Session session) {
        JsonObject header = new JsonObject();
        header.addProperty("session_id", session.getId());
        String encodedHeader = Base64.getUrlEncoder().encodeToString(new Gson().toJson(header).getBytes());
        byte[] signature = sign(session);
        String encodedSignature = Base64.getUrlEncoder().encodeToString(signature);
        return encodedHeader + TOKEN_SEPARATOR + encodedSignature;
    }


    private static byte[] sign(Session session) {
        String IAM_API = "https://www.googleapis.com/auth/iam";
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault().createScoped(IAM_API);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (credentials instanceof ServiceAccountCredentials) {
            return ((ServiceAccountCredentials) credentials).sign(session.getId().getBytes());
        }
        else if (credentials instanceof ComputeEngineCredentials) {
            return ((ComputeEngineCredentials) credentials).sign(session.getId().getBytes());
        }
        else {
            throw new RuntimeException("Invalid credentials");
        }
    }

}
