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

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.lang.invoke.MethodHandles;
import java.util.List;

import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.oauth.RefreshToken;
import com.google.cloud.broker.oauth.RefreshTokenUtils;
import com.google.cloud.broker.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevokeRefreshTokens {

    private static final Class<?> klass = MethodHandles.lookup().lookupClass();
    private static final Logger logger = LoggerFactory.getLogger(klass);

    public static void main(String[] args) {
        long numHours;
        if (args.length == 1) {
            numHours = Long.parseLong(args[0]);
        }
        else {
            throw new IllegalArgumentException("Wrong arguments");
        }
        long numMilliseconds = numHours * 3600 * 1000;
        long now = TimeUtils.currentTimeMillis();
        List<Model> models = AbstractDatabaseBackend.getInstance().getAll(RefreshToken.class);
        int numRevokedToken = 0;
        for (Model model : models) {
            RefreshToken token = (RefreshToken) model;
            if (now >= token.getCreationTime() + numMilliseconds) {
                // Revoke the token
                RefreshTokenUtils.revoke(token);
                // Delete the token from the database
                AbstractDatabaseBackend.getInstance().delete(token);
                numRevokedToken++;
            }
        }
        logger.info(klass.getSimpleName() + " - Revoked and deleted refresh token(s): " + numRevokedToken);
    }

}
