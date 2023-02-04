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

import com.google.cloud.broker.checks.CheckResult;
import com.google.cloud.broker.settings.AppSettings;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.redisson.Redisson;
import org.redisson.api.NodesGroup;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.config.Config;

public class RedisCache extends AbstractRemoteCache {

  private RedissonClient client;

  public RedisCache() {}

  private RedissonClient getClient() {
    if (client == null) {
      String host = AppSettings.getInstance().getString(AppSettings.REDIS_CACHE_HOST);
      Integer port = AppSettings.getInstance().getInt(AppSettings.REDIS_CACHE_PORT);
      Config config = new Config();
      config
          .useSingleServer()
          .setAddress(String.format("redis://%s:%s", host, port))
          .setDatabase(AppSettings.getInstance().getInt(AppSettings.REDIS_CACHE_DB));
      client = Redisson.create(config);
    }
    return client;
  }

  public byte[] get(String key) {
    RBucket<byte[]> bucket = getClient().getBucket(key, ByteArrayCodec.INSTANCE);
    return bucket.get();
  }

  public void set(String key, byte[] value) {
    RBucket<byte[]> bucket = getClient().getBucket(key, ByteArrayCodec.INSTANCE);
    bucket.set(value);
  }

  public void set(String key, byte[] value, int expireIn) {
    RBucket<byte[]> bucket = getClient().getBucket(key, ByteArrayCodec.INSTANCE);
    bucket.set(value, expireIn, TimeUnit.SECONDS);
  }

  public void delete(String key) {
    RBucket<byte[]> bucket = getClient().getBucket(key, ByteArrayCodec.INSTANCE);
    bucket.delete();
  }

  public Lock acquireLock(String lockName) {
    RLock lock = getClient().getLock(lockName);
    lock.lock();
    return lock;
  }

  @Override
  public CheckResult checkConnection() {
    try {
      NodesGroup nodesGroup = getClient().getNodesGroup();
      nodesGroup.pingAll();
      return new CheckResult(true);
    } catch (Exception e) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      return new CheckResult(false, sw.toString());
    }
  }
}
