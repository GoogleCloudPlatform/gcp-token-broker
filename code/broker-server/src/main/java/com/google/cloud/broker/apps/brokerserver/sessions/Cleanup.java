package com.google.cloud.broker.apps.brokerserver.sessions;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.utils.TimeUtils;

public class Cleanup {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {
        Integer limit = null;
        if (args.length > 0) {
            limit = Integer.parseInt(args[0]);
        }
        long now = TimeUtils.currentTimeMillis();
        int numDeletedSessions = AbstractDatabaseBackend.getInstance().deleteStaleItems(
            Session.class, "expiresAt", now, limit);
        logger.info("SessionCleanup - Deleted stale session(s): " + numDeletedSessions);
    }

}
