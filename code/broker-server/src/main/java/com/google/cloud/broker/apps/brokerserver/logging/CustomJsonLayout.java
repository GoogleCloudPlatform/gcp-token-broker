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

import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import ch.qos.logback.contrib.jackson.JacksonJsonFormatter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import com.google.cloud.broker.apps.brokerserver.ClientAddressServerInterceptor;
import com.google.cloud.broker.authentication.backends.AbstractAuthenticationBackend;
import static com.google.cloud.broker.apps.brokerserver.logging.LoggingUtils.*;

public class CustomJsonLayout extends JsonLayout {

    public CustomJsonLayout() {
        includeLevel = false;
        includeTimestamp = false;
        includeContextName = false;
        includeMDC = false;
        includeThreadName = false;
        includeMessage = false;
        includeLoggerName = true;
        appendLineSeparator = true;
        jsonFormatter = new JacksonJsonFormatter();
    }

    private static String pop(Map<String, String> map, String key) {
        if (map.containsKey(key)) {
            String value = map.get(key);
            map.remove(key);
            return value;
        }
        else {
            return null;
        }
    }

    private static void putIfExists(Map<String, String> sourceMap, String sourceKey, Map<String, String> destMap, String destKey) {
        String value = pop(sourceMap, sourceKey);
        if (value != null) {
            destMap.put(destKey, value);
        }
    }

    @Override
    protected Map toJsonMap(ILoggingEvent iLoggingEvent) {
        Map map = super.toJsonMap(iLoggingEvent);

        if (! MDC_LOG_TYPE_VALUE_AUDIT.equals(MDC.get(MDC_LOG_TYPE_KEY))) {
            return map;
        }

        Map<String, String> mdc = MDC.getCopyOfContextMap();

        // Basics
        this.add("serviceName", true, SERVICE_NAME, map);
        this.add("methodName", true, pop(mdc, MDC_METHOD_NAME_KEY), map);
        this.add("severity", true, String.valueOf(iLoggingEvent.getLevel()), map);

        // Request metadata
        HashMap<String, String> requestMetadata = new HashMap<>();
        requestMetadata.put("callerAddress", ClientAddressServerInterceptor.CLIENT_ADDRESS_CONTEXT_KEY.get());
        this.addMap("requestMetadata", true, requestMetadata, map);

        // Authentication info
        HashMap<String, String> authenticationInfo = new HashMap<>();
        putIfExists(mdc, AbstractAuthenticationBackend.AUTHENTICATED_USER, authenticationInfo, "authenticatedUser");
        putIfExists(mdc, MDC_AUTH_MODE_KEY, authenticationInfo, "authenticationMode");
        putIfExists(mdc, MDC_AUTH_MODE_DELEGATED_SESSION_ID_KEY, authenticationInfo, "sessionId");
        putIfExists(mdc, MDC_AUTH_MODE_PROXY_IMPERSONATED_USER_KEY, authenticationInfo, "impersonatedUser");
        this.addMap("authenticationInfo", true, authenticationInfo, map);

        // Status
        HashMap<String, String> status = new HashMap<>();
        putIfExists(mdc, MDC_STATUS_CODE_KEY, status, "code");
        putIfExists(mdc, MDC_STATUS_MESSAGE_KEY, status, "message");
        this.addMap("status", true, status, map);

        // Extras
        HashMap<String, String> extras = new HashMap<>();
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            extras.put(
                StringUtils.removeStart(entry.getKey(), MDC_KEY_PREFIX),
                entry.getValue()
            );
        }
        this.addMap("extras", true, extras, map);

        return map;
    }
}
