package com.google.cloud.broker.client.hadoop.fs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.hadoop.conf.Configuration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.cloud.hadoop.util.AccessTokenProvider;
import com.google.common.flogger.GoogleLogger;

public class RefreshTokenAuth2Provider implements AccessTokenProvider {
    private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

    private static final int CONNECT_TIMEOUT = 30 * 1000;
    private static final int READ_TIMEOUT = 30 * 1000;
    public final static String CONFIG_REFRESH_TOKEN = "fs.gs.auth.refresh.token";
    public final static String CONFIG_TOKEN_ENDPOINT = "fs.gs.auth.token.endpoint";
    public final static String CONFIG_CLIENT_ID = "fs.gs.auth.client.id";
    public final static String CONFIG_CLIENT_SECRET = "fs.gs.auth.client.secret";
    private final static AccessToken EXPIRED_TOKEN = new AccessToken("", -1L);

    private Configuration config;
    private AccessToken accessToken = EXPIRED_TOKEN;

    public AccessToken getAccessToken() {

        try {
            refresh();
        } catch (IOException e) {
            logger.atSevere().log("Couldn't refresh the access token", e);
        }
        return this.accessToken;
    }

    public void refresh() throws IOException {
        logger.atFine().log("Refreshing access-token based token");
        String tokenEndpoint = this.config.get(CONFIG_TOKEN_ENDPOINT);
        String refreshToken = this.config.get(CONFIG_REFRESH_TOKEN);
        String clientId = this.config.get(CONFIG_CLIENT_ID);
        String clientSecret = this.config.get(CONFIG_CLIENT_SECRET);
        logger.atFine().log("Refresh token calling endpoint '" + tokenEndpoint + "' with client id '" + clientId + "'");
        try {
            this.accessToken =  getAccessToken(tokenEndpoint, clientId, clientSecret, refreshToken);
        } catch (IOException e) {
            logger.atSevere().log("Couldn't refresh token", e);
        }
    }

    public AccessToken getAccessToken(String tokenEndpoint,
                                                           String clientId,
                                                           String clientSecret,
                                                           String refreshToken
    ) throws IOException {

        AccessToken token = null;
        HttpURLConnection conn = null;
        logger.atFine().log("Get a new access token using the refresh token grant flow");
        try {
            URL url = new URL(tokenEndpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            String payload = "grant_type=refresh_token&refresh_token=" + refreshToken + "&client_id=" + clientId + "&client_secret=" + clientSecret;
            conn.getOutputStream().write(payload.getBytes("UTF-8"));

            int httpResponseCode = conn.getResponseCode();
            logger.atFine().log("Response " + httpResponseCode);

            String responseContentType = conn.getHeaderField("Content-Type");

            if (httpResponseCode == HttpURLConnection.HTTP_OK
                    && responseContentType.startsWith("application/json")) {
                logger.atFine().log("Received a 200 with presumably a new access token");
                InputStream httpResponseStream = conn.getInputStream();
                token = parseTokenFromStream(httpResponseStream);
            } else {
                logger.atFine().log("Refresh token grant flow failed");
                InputStream stream = conn.getErrorStream();
                if (stream == null) {
                    // no error stream, try the original input stream
                    stream = conn.getInputStream();
                }
                String responseBody = consumeInputStream(stream);
                logger.atSevere().log("Received following response '" + responseBody + "' with code '" + httpResponseCode + "'");
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return token;
    }

    private String consumeInputStream(InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
    }

    private static AccessToken parseTokenFromStream(
            InputStream httpResponseStream) throws IOException {
        AccessToken token;
        try {
            int expiryPeriodInSecs = 0;

            JsonFactory jf = new JsonFactory();
            JsonParser jp = jf.createJsonParser(httpResponseStream);
            String fieldName, fieldValue;
            jp.nextToken();
            String accessToken = "";
            while (jp.hasCurrentToken()) {
                if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
                    fieldName = jp.getCurrentName();
                    jp.nextToken();  // field value
                    fieldValue = jp.getText();

                    if (fieldName.equals("access_token")) {
                        accessToken = fieldValue;
                    }

                    if (fieldName.equals("expires_in")) {
                        expiryPeriodInSecs = Integer.parseInt(fieldValue);
                    }
                }
                jp.nextToken();
            }

            token = new AccessToken(accessToken, expiryPeriodInSecs * 1000L);
            jp.close();
        } finally {
            httpResponseStream.close();
        }
        return token;
    }

    @Override
    public void setConf(Configuration config) {
        this.config = config;
    }

    @Override
    public Configuration getConf() {
        return this.config;
    }

}