package com.google.cloud.broker.caching.remote;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatastoreCacheCleanup {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {
        Integer limit = null;
        if (args.length > 0) {
            limit = Integer.parseInt(args[0]);
        }
        CloudDatastoreCache cache = new CloudDatastoreCache();
        int numDeletedItems = cache.deleteExpiredItems(limit);
        logger.info("DatastoreCacheCleanup - Deleted expired item(s): " + numDeletedItems);
    }

}
