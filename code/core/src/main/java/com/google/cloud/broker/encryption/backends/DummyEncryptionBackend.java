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

import com.google.cloud.broker.checks.CheckResult;

/**
 * Dummy encryption backend that does not encrypt nor decrypt anything. Use only for testing. Do NOT
 * use in production!
 */
public class DummyEncryptionBackend extends AbstractEncryptionBackend {

  @Override
  public byte[] decrypt(byte[] cipherText) {
    return cipherText;
  }

  @Override
  public byte[] encrypt(byte[] plainText) {
    return plainText;
  }

  @Override
  public CheckResult checkConnection() {
    return new CheckResult(true);
  }
}
