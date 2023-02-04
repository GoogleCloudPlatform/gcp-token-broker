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

package com.google.cloud.broker.apps.brokerserver.logging;

import io.grpc.StatusRuntimeException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class LoggingUtils {
  public static final String SERVICE_NAME = "GCPTokenBroker";
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Mapped Diagnostic Context (MDC) keys and values ----------------------

  public static final String MDC_KEY_PREFIX = "mdc-key-";

  // Log type
  public static final String MDC_LOG_TYPE_KEY = MDC_KEY_PREFIX + "logType";
  public static final String MDC_LOG_TYPE_VALUE_AUDIT = "audit";

  // Authentication mode
  public static final String MDC_AUTH_MODE_KEY = MDC_KEY_PREFIX + "authenticationMode";
  public static final String MDC_AUTH_MODE_VALUE_DIRECT = "direct";
  public static final String MDC_AUTH_MODE_VALUE_DELEGATED = "delegated";
  public static final String KDC_AUTH_MODE_VALUE_PROXY = "proxy";
  public static final String MDC_AUTH_MODE_DELEGATED_SESSION_ID_KEY =
      MDC_KEY_PREFIX + "delegatedSessionId";
  public static final String MDC_AUTH_MODE_PROXY_IMPERSONATED_USER_KEY =
      MDC_KEY_PREFIX + "impersonatedUser";

  // Status
  public static final String MDC_STATUS_CODE_KEY = MDC_KEY_PREFIX + "statusCode";
  public static final String MDC_STATUS_MESSAGE_KEY = MDC_KEY_PREFIX + "statusMessage";

  // Basics
  public static final String MDC_METHOD_NAME_KEY = MDC_KEY_PREFIX + "methodName";

  // Extras
  public static final String MDC_ACCESS_TOKEN_USER_KEY = MDC_KEY_PREFIX + "accessTokenUser";
  public static final String MDC_SESSION_ID_KEY = MDC_KEY_PREFIX + "sessionId";
  public static final String MDC_OWNER_KEY = MDC_KEY_PREFIX + "owner";
  public static final String MDC_RENEWER_KEY = MDC_KEY_PREFIX + "renewer";
  public static final String MDC_SCOPES_KEY = MDC_KEY_PREFIX + "scopes";
  public static final String MDC_TARGET_KEY = MDC_KEY_PREFIX + "target";

  public static void successAuditLog() {
    MDC.put(MDC_LOG_TYPE_KEY, MDC_LOG_TYPE_VALUE_AUDIT);
    MDC.put(MDC_STATUS_CODE_KEY, "OK");
    MDC.put(MDC_STATUS_MESSAGE_KEY, "Success");
    logger.info(String.format("%s %s %s", SERVICE_NAME, MDC.get(MDC_METHOD_NAME_KEY), "OK"));
  }

  public static void errorAuditLog(StatusRuntimeException e) {
    MDC.put(MDC_LOG_TYPE_KEY, MDC_LOG_TYPE_VALUE_AUDIT);
    MDC.put(MDC_STATUS_CODE_KEY, e.getStatus().getCode().toString());
    MDC.put(MDC_STATUS_MESSAGE_KEY, e.getStatus().getDescription());
    logger.error(
        String.format(
            "%s %s %s", SERVICE_NAME, MDC.get(MDC_METHOD_NAME_KEY), e.getStatus().getCode()));
  }
}
