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

package com.google.cloud.broker.oauth;

import com.google.cloud.broker.database.models.CreationTimeModel;
import com.google.cloud.broker.encryption.backends.AbstractEncryptionBackend;
import com.google.cloud.broker.utils.TimeUtils;
import com.google.common.base.Charsets;

import java.util.HashMap;

public class RefreshToken extends CreationTimeModel {
    public static String ID = "id";
    public static String VALUE = "value";
    public static String CREATION_TIME = "creation_time";

    /**
     * Expected schema:
     *
     * RefreshToken:
     *   - id: String            => GSuite email address (e.g. alice@example.com)
     *   - value: byte[]         => The actual OAuth refresh token (Recommendation: encrypt this value)
     *   - creation_time: Long   => The time when the object was created (in milliseconds)
     */
    public RefreshToken(HashMap<String, Object> values) {
        super(values);
    }

    public static RefreshToken create(String refreshToken, String principal, AbstractEncryptionBackend aead) {
        HashMap<String,Object> values = new HashMap<>();
        values.put(ID, principal);
        values.put(VALUE, aead.encrypt(refreshToken.getBytes(Charsets.UTF_8)));
        values.put(CREATION_TIME, TimeUtils.currentTimeMillis());
        return new RefreshToken(values);
    }
}
