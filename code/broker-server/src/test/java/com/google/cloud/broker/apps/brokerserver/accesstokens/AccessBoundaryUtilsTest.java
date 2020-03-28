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

import static org.junit.Assert.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.broker.utils.CloudStorageUtils;
import org.junit.Test;

public class AccessBoundaryUtilsTest {

    private static final String MOCK_BUCKET = "//storage.googleapis.com/projects/_/buckets/example";

    /**
     * Check that the access token isn't modified if the target is null.
     */
    @Test
    public void testTargetNull() throws IOException {
        GoogleCredentials credentials = CloudStorageUtils.getCloudStorageCredentials();
        com.google.auth.oauth2.AccessToken rawAccessToken = credentials.refreshAccessToken();
        AccessToken accessToken = new AccessToken(rawAccessToken.getTokenValue(), rawAccessToken.getExpirationTime().getTime());
        AccessToken boundedToken = AccessBoundaryUtils.addAccessBoundary(accessToken, null);
        assertEquals(boundedToken.getValue(), rawAccessToken.getTokenValue());
        assertEquals(boundedToken.getExpiresAt(), rawAccessToken.getExpirationTime().getTime());
    }

    /**
     * Same as testTargetNull but with the empty string value.
     */
    @Test
    public void testTargetEmptyString() throws IOException {
        GoogleCredentials credentials = CloudStorageUtils.getCloudStorageCredentials();
        com.google.auth.oauth2.AccessToken rawAccessToken = credentials.refreshAccessToken();
        AccessToken accessToken = new AccessToken(rawAccessToken.getTokenValue(), rawAccessToken.getExpirationTime().getTime());
        AccessToken boundedToken = AccessBoundaryUtils.addAccessBoundary(accessToken, "");
        assertEquals(boundedToken.getValue(), rawAccessToken.getTokenValue());
        assertEquals(boundedToken.getExpiresAt(), rawAccessToken.getExpirationTime().getTime());
    }

    /**
     * Check that the access token is modified if the target is set to a resource.
     */
    @Test
    public void testBoundary() throws IOException {
        GoogleCredentials credentials = CloudStorageUtils.getCloudStorageCredentials();
        com.google.auth.oauth2.AccessToken rawAccessToken = credentials.refreshAccessToken();
        AccessToken accessToken = new AccessToken(rawAccessToken.getTokenValue(), rawAccessToken.getExpirationTime().getTime());
        AccessToken boundedToken = AccessBoundaryUtils.addAccessBoundary(accessToken, MOCK_BUCKET);
        assertNotEquals(boundedToken.getValue(), rawAccessToken.getTokenValue());
        assertNotEquals(boundedToken.getExpiresAt(), rawAccessToken.getExpirationTime().getTime());
        // TODO: Verify the token is bounded to the bucket
    }

}
