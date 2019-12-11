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

package com.google.cloud.broker.apps.brokerserver;

import io.grpc.*;


public class ClientAddressServerInterceptor implements ServerInterceptor {

    public static final Context.Key<String> CLIENT_ADDRESS_CONTEXT_KEY = Context.key("ClientAddress");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String clientAddress = serverCall.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
        Context ctx = Context.current().withValue(CLIENT_ADDRESS_CONTEXT_KEY, clientAddress);
        return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
    }
}