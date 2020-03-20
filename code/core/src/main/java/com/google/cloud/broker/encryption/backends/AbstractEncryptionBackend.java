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

package com.google.cloud.broker.encryption.backends;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.InstanceUtils;
import com.google.cloud.broker.checks.CheckResult;

public abstract class AbstractEncryptionBackend {

    private static AbstractEncryptionBackend instance;
    public abstract byte[] decrypt(byte[] cipherText);
    public abstract byte[] encrypt(byte[] plainText);
    public abstract CheckResult checkConnection();

    public static AbstractEncryptionBackend getInstance() {
        String className = AppSettings.getInstance().getString(AppSettings.ENCRYPTION_BACKEND);
        if (instance == null || !className.equals(instance.getClass().getCanonicalName())) {
            instance = (AbstractEncryptionBackend) InstanceUtils.invokeConstructor(className);
        }
        return instance;
    }

}
