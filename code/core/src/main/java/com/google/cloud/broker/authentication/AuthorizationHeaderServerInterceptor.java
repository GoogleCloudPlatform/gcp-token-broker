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

package com.google.cloud.broker.authentication;

import io.grpc.*;


public class AuthorizationHeaderServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> BROKER_AUTHORIZATION_METADATA_KEY = Metadata.Key.of("broker-authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Context.Key<String> BROKER_AUTHORIZATION_CONTEXT_KEY = Context.key("BrokerAuthorizationHeader");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String authorizationHeader = metadata.get(BROKER_AUTHORIZATION_METADATA_KEY);
        Context ctx = Context.current().withValue(BROKER_AUTHORIZATION_CONTEXT_KEY, authorizationHeader);
        return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
    }
}