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

package com.google.cloud.broker.apps.brokerserver.logging;

import java.lang.invoke.MethodHandles;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.google.cloud.broker.apps.brokerserver.ClientAddressServerInterceptor;


public class LoggingUtils {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void logSuccess(String endpointName) {
        // Log success message
        MDC.put("status_code", "OK");
        MDC.put("endpoint", endpointName);
        MDC.put("client_address", ClientAddressServerInterceptor.CLIENT_ADDRESS_CONTEXT_KEY.get());
        logger.info("success");
        MDC.clear();
    }

    public static void logError(StatusRuntimeException e, String endpointName) {
        // Log error message
        MDC.put("endpoint", endpointName);
        MDC.put("status_code", e.getStatus().getCode().toString());
        MDC.put("status_description", e.getStatus().getDescription());
        MDC.put("client_address", ClientAddressServerInterceptor.CLIENT_ADDRESS_CONTEXT_KEY.get());
        logger.info("reject");
        MDC.clear();
    }

}
