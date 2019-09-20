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

import java.util.HashMap;

public class RefreshToken extends CreationTimeModel {

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

}
