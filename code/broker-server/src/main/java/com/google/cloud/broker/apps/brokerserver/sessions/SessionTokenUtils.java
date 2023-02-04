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

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.grpc.Status;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

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
    } catch (ArrayIndexOutOfBoundsException | JsonSyntaxException e) {
      throw Status.UNAUTHENTICATED.withDescription("Session token is invalid").asRuntimeException();
    }
  }

  public static Session getSessionFromRawToken(String rawToken) {
    SessionToken sessionToken = unmarshallSessionToken(rawToken);

    // Fetch session from the database
    Session session;
    try {
      session =
          (Session)
              AbstractDatabaseBackend.getInstance().get(Session.class, sessionToken.getSessionId());
    } catch (DatabaseObjectNotFound e) {
      throw Status.UNAUTHENTICATED
          .withDescription("Session token is invalid or has expired")
          .asRuntimeException();
    }

    // Verify that the provided signature is valid
    if (verifySignature(session.getId().getBytes(), sessionToken.getSignature())) {
      return session;
    } else {
      throw Status.UNAUTHENTICATED.withDescription("Invalid session token").asRuntimeException();
    }
  }

  public static boolean verifySignature(byte[] data, byte[] signatureToVerify) {
    // Load all public certificates for the broker service account
    String url =
        "https://www.googleapis.com/service_accounts/v1/metadata/x509/"
            + getBrokerServiceAccountEmail();
    InputStream is;
    try {
      is = new URL(url).openStream();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
    Map<String, String> map = new Gson().fromJson(reader, Map.class);

    // Loop through the list of public certificates
    for (String entry : map.values()) {
      X509Certificate certificate;
      try {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        certificate =
            (X509Certificate)
                certificateFactory.generateCertificate(new ByteArrayInputStream(entry.getBytes()));
      } catch (CertificateException e) {
        // Invalid certificate. Move on to the next.
        continue;
      }

      PublicKey publicKey = certificate.getPublicKey();
      try {
        Signature signature = Signature.getInstance("SHA256WithRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        if (signature.verify(signatureToVerify)) {
          // Signature was verified with the current certificate.
          return true;
        }
      } catch (SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
        // This signature doesn't work with the current certificate.
        // Ignore and move on to the next.
        continue;
      }
    }
    return false;
  }

  public static String marshallSessionToken(Session session) {
    JsonObject header = new JsonObject();
    header.addProperty("session_id", session.getId());
    String encodedHeader =
        Base64.getUrlEncoder().encodeToString(new Gson().toJson(header).getBytes());
    byte[] signature = sign(session.getId().getBytes());
    String encodedSignature = Base64.getUrlEncoder().encodeToString(signature);
    return encodedHeader + TOKEN_SEPARATOR + encodedSignature;
  }

  public static String getBrokerServiceAccountEmail() {
    try {
      return ((ServiceAccountSigner) GoogleCredentials.getApplicationDefault()).getAccount();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] sign(byte[] data) {
    String IAM_API = "https://www.googleapis.com/auth/iam";
    try {
      return ((ServiceAccountSigner)
              GoogleCredentials.getApplicationDefault().createScoped(IAM_API))
          .sign(data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
