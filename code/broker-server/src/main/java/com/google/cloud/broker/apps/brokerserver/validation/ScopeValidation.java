package com.google.cloud.broker.apps.brokerserver.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.grpc.Status;

import com.google.cloud.broker.settings.AppSettings;

public class ScopeValidation {

    public static void validateScopes(List<String> scopes) {
        List<String> whitelist = AppSettings.getInstance().getStringList(AppSettings.SCOPES_WHITELIST);
        Set<String> scopeSet = new HashSet<String>(scopes);
        Set<String> whitelistSet = new HashSet<String>(whitelist);
        if (!whitelistSet.containsAll(scopeSet)) {
            throw Status.PERMISSION_DENIED
                .withDescription(String.format("`[%s]` are not whitelisted scopes", String.join(",", scopes)))
                .asRuntimeException();
        }
    }

}
