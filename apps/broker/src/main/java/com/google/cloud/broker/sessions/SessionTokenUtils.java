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

import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.grpc.Status;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.cloud.broker.settings.AppSettings;


public class SessionTokenUtils {

    private static String TOKEN_SEPARATOR = ".";


    private static SessionToken unmarshallSessionToken(String token) {
        String[] split = token.split(Pattern.quote(TOKEN_SEPARATOR));
        String sessionId = null;
        byte[] encryptedPassword = null;
        try {
            String headerString = new String(Base64.getUrlDecoder().decode(split[0]));
            JsonObject header = (JsonObject) new JsonParser().parse(headerString);
            sessionId = header.get("session_id").getAsString();
            encryptedPassword = Base64.getUrlDecoder().decode(split[1]);
        }
        catch (ArrayIndexOutOfBoundsException | JsonSyntaxException e) {
            throw Status.UNAUTHENTICATED.withDescription("Session token is invalid").asRuntimeException();
        }

        return new SessionToken(sessionId, encryptedPassword);
    }


    public static Session getSessionFromRawToken(String rawToken) {
        SessionToken sessionToken = unmarshallSessionToken(rawToken);

        // Fetch session from the database
        Session session = null;
        try {
            session = (Session) Model.get(Session.class, sessionToken.getSessionId());
        }
        catch (DatabaseObjectNotFound e) {
            throw Status.UNAUTHENTICATED.withDescription("Session token is invalid or has expired").asRuntimeException();
        }

        // Decrypt the provided password
        String cryptoKey = AppSettings.requireProperty("ENCRYPTION_SESSION_TOKEN_CRYPTO_KEY");
        byte[] decryptedPassword = AbstractEncryptionBackend.getInstance().decrypt(cryptoKey, sessionToken.getEncryptedPassword());

        // Verify that the provided password matches that of the session
        boolean samePasswords = MessageDigest.isEqual(
            decryptedPassword,
            session.getValue("password").toString().getBytes()
        );
        if (! samePasswords) {
            throw Status.UNAUTHENTICATED.withDescription("Invalid session token").asRuntimeException();
        }

        return session;
    }


    public static String marshallSessionToken(Session session) {
        String password = session.getValue("password").toString();
        byte[] encryptedPassword = AbstractEncryptionBackend.getInstance().encrypt(
            AppSettings.requireProperty("ENCRYPTION_SESSION_TOKEN_CRYPTO_KEY"),
            password.getBytes()
        );
        JsonObject header = new JsonObject();
        header.addProperty("session_id", session.getValue("id").toString());
        String encodedHeader = Base64.getUrlEncoder().encodeToString(new Gson().toJson(header).getBytes());
        String encodedPassword = Base64.getUrlEncoder().encodeToString(encryptedPassword);
        return encodedHeader + TOKEN_SEPARATOR + encodedPassword;
    }

}
