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

package com.google.cloud.broker.caching.remote;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.utils.TimeUtils;
import com.google.cloud.datastore.*;

public class CloudDatastoreCache extends AbstractRemoteCache {

    public static class DatastoreLock implements Lock {

        public final static String LOCK_KIND = "Lock";
        private Key key;
        private Datastore datastore;
        int deadlockTimeout = 5 * 1000;  // Maximum time that someone can hold the lock for. In milliseconds.
        int patienceTimeout = 15 * 1000;  // Maximum time that one is willing to wait. In milliseconds.
        int waitInterval = 500;  // Wait time interval. In milliseconds.

        public DatastoreLock(String key) {
            datastore = getService();
            KeyFactory keyFactory = datastore.newKeyFactory().setKind(LOCK_KIND);
            this.key = keyFactory.newKey(key);
        }

        private void waitForAWhile() {
            try {
                Thread.sleep(waitInterval);
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }

        @Override
        public void lock() {
            long start = TimeUtils.currentTimeMillis();
            while(true) {
                if (TimeUtils.currentTimeMillis() > start + patienceTimeout) {
                    // We've waited too long. Bail out.
                    throw new RuntimeException("Ran out of patience");
                }
                // Look up the lock entry
                Transaction transaction = datastore.newTransaction();
                try {
                    Entity lock = transaction.get(key);
                    if (lock == null) {  // No one has created the lock yet
                        // Let's attempt to create the lock
                        Entity.Builder builder = Entity.newBuilder(key);
                        builder.set("creation_time", TimeUtils.currentTimeMillis());
                        Entity entity = builder.build();
                        try {
                            transaction.add(entity);
                            transaction.commit();
                        } catch (DatastoreException e) {
                            if (e.getReason().equals("ALREADY_EXISTS") || e.getReason().equals("ABORTED")) {
                                // Someone just beat us to the punch, so we wait for a while...
                                waitForAWhile();
                                // ... then try again
                                continue;
                            } else {
                                // Unhandled error
                                throw new RuntimeException(e);
                            }
                        }
                        // We've got the lock. Our work is done here.
                        return;
                    } else {
                        // Someone else already owns the lock.
                        // Check if the lock is still alive
                        if (TimeUtils.currentTimeMillis() > lock.getLong("creation_time") + deadlockTimeout) {
                            try {
                                // Lock was been created too long ago, so we delete it to avoid deadlock
                                transaction.delete(key);
                                transaction.commit();
                            } catch (DatastoreException e) {
                                if (e.getReason().equals("ABORTED")) {
                                    // Someone else might have tried to delete or update the lock,
                                    // so we ignore the deletion failure.
                                }
                                else {
                                    // Unhandled error
                                    throw new RuntimeException(e);
                                }
                            }
                        } else {
                            // Someone else is holding the lock, so we wait for a while...
                            waitForAWhile();
                        }
                    }
                }
                finally {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                }
            }
        }

        @Override
        public void unlock() {
            datastore.delete(key);
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    public final static String CACHE_KIND = "Cache";
    public final static String CACHE_VALUE_FIELD = "value";
    public final static String CACHE_EXPIRY_FIELD = "expiry";

    private static Datastore getService() {
        String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
        return DatastoreOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    @Override
    public byte[] get(String key) {
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
        Key datastoreKey = keyFactory.newKey(key);
        Entity entity = datastore.get(datastoreKey);
        if (entity != null) {
            long expiry = entity.getLong(CACHE_EXPIRY_FIELD);
            long now = TimeUtils.currentTimeMillis();
            if (expiry == 0 || now < expiry) {
                return entity.getBlob(CACHE_VALUE_FIELD).toByteArray();
            }
        }
        return null;
    }

    @Override
    public void set(String key, byte[] value) {
        set(key, value, 0);
    }

    @Override
    public void set(String key, byte[] value, int expireIn) {
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
        Key datastoreKey = keyFactory.newKey(key);
        Entity.Builder builder = Entity.newBuilder(datastoreKey);
        long now = TimeUtils.currentTimeMillis();
        builder.set(CACHE_VALUE_FIELD, BlobValue.of(Blob.copyFrom(value)));
        builder.set(CACHE_EXPIRY_FIELD, now + expireIn * 1000);
        Entity entity = builder.build();
        datastore.put(entity);
    }

    @Override
    public void delete(String key) {
        Datastore datastore = getService();
        KeyFactory keyFactory = datastore.newKeyFactory().setKind(CACHE_KIND);
        Key datastoreKey = keyFactory.newKey(key);
        datastore.delete(datastoreKey);
    }

    @Override
    public Lock acquireLock(String lockName) {
        DatastoreLock lock = new DatastoreLock(lockName);
        lock.lock();
        return lock;
    }

}
