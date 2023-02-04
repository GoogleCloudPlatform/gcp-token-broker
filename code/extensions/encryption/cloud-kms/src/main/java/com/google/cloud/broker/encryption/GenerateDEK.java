/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.broker.encryption;

import com.google.cloud.broker.encryption.backends.CloudKMSBackend;
import com.google.cloud.broker.settings.AppSettings;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Command-line utility that generates a new data encryption key (DEK) and stores it in GCS */
public class GenerateDEK {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static void main(String[] args) {
    String dekUri = null;
    String kekUri = null;
    if (args.length == 0) {
      dekUri = AppSettings.getInstance().getString(AppSettings.ENCRYPTION_DEK_URI);
      kekUri = AppSettings.getInstance().getString(AppSettings.ENCRYPTION_KEK_URI);
    } else if (args.length == 2) {
      dekUri = args[0];
      kekUri = args[1];
    } else {
      logger.error("Invalid parameters");
      System.exit(1);
    }

    logger.info("Generating DEK...");
    logger.info("Wrapping with KEK `" + kekUri + "`...");
    logger.info("Writing to `" + dekUri + "`...");
    try {
      CloudKMSBackend.generateAndWriteKeyset(dekUri, kekUri);
    } catch (Exception e) {
      logger.error("Failed to generate and write DEK");
      e.printStackTrace(System.err);
      System.exit(1);
    }
    logger.info("Done.");
  }
}
