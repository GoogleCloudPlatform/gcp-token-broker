package com.google.cloud.broker.apps.brokerserver.sessions;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;
import com.google.cloud.broker.utils.TimeUtils;

public class Cleanup {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {
        long now = TimeUtils.currentTimeMillis();
        int numDeletedSessions = AbstractDatabaseBackend.getInstance().deleteStaleItems(Session.class, "expiresAt", now);
        logger.info("SessionCleanup - Deleted stale session(s): " + numDeletedSessions);
    }

}
