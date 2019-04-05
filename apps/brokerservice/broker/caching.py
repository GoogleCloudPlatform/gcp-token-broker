# Copyright 2019 Google LLC
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from datetime import timedelta, datetime

try:
    import redis
    import redis_lock
except ImportError:
    pass

from common.utils import import_module_from_string
from common.conf import settings


class CacheBackendBase():

    def get(self, key):
        raise NotImplementedError

    def set(self, key, value, timeout=None):
        raise NotImplementedError

    def delete(self, key):
        raise NotImplementedError

    def acquire_lock(self, lock_name):
        raise NotImplementedError

    def release_lock(self, lock):
        raise NotImplementedError


class LocalCacheBackend():
    """
    Caches data in the process' local memory.
    """

    NEVER = -1

    def __init__(self):
        self._CACHE = {}
        self._EXPIRY_TIMES = {}

    def get(self, key):
        value = self._CACHE.get(key)
        if value is not None:
            expiry_time = self._EXPIRY_TIMES[key]
            if expiry_time == LocalCacheBackend.NEVER or expiry_time > datetime.utcnow():
                return value

    def set(self, key, value, timeout=None):
        if timeout is None:
            expiry_time = LocalCacheBackend.NEVER
        else:
            expiry_time = datetime.utcnow() + timedelta(seconds=timeout)
        self._EXPIRY_TIMES[key] = expiry_time
        self._CACHE[key] = value

    def delete(self, key):
        del self._CACHE[key]
        del self._EXPIRY_TIMES[key]


class RedisCacheBackend(CacheBackendBase):
    """
    Caches data in a Redis database.
    """

    def __init__(self):
        self.client = redis.StrictRedis(
            host=settings.REDIS_CACHE_HOST,
            port=settings.REDIS_CACHE_PORT,
            db=settings.REDIS_CACHE_DB
        )

    def get(self, key):
        return self.client.get(key)

    def set(self, key, value, timeout=None):
        self.client.set(key, value)
        if timeout is not None:
            self.client.expire(key, timeout)

    def delete(self, key):
        self.client.delete(key)

    def acquire_lock(self, lock_name):
        lock = redis_lock.Lock(self.client, lock_name)
        lock.acquire()
        return lock

    def release_lock(self, lock):
        lock.release()


def get_cache():
    global _cache
    if _cache is None:
        klass = import_module_from_string(settings.CACHE_BACKEND)
        _cache = klass()
    return _cache


def get_local_cache():
    global _local_cache
    if _local_cache is None:
        _local_cache = LocalCacheBackend()
    return _local_cache


_cache = None
_local_cache = None