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

import json
import uuid
from datetime import datetime
from operator import attrgetter

try:
    import redis
except ImportError:
    pass

try:
    from google.cloud import datastore
except ImportError:
    pass


from common.utils import import_module_from_string, datetime_to_integer
from common.conf import settings


class DatabaseObjectNotFound(Exception):
    pass


class Model():
    _DEFAULT_FIELDS = ['id']
    FIELDS = None

    def __init__(self, **kwargs):
        for field in self.FIELDS + self._DEFAULT_FIELDS:
            setattr(self, field, kwargs.get(field))
        if self.id is None:
            self.id = str(uuid.uuid4())

    def save(self):
        get_database_backend(self.__class__).save(self)

    def delete(self):
        get_database_backend(self.__class__).delete(self.id)

    @classmethod
    def exist(cls, *object_ids):
        try:
            for object_id in object_ids:
                get_database_backend(cls).get(object_id)
            return True
        except DatabaseObjectNotFound:
            return False

    @classmethod
    def get(cls, object_id):
        return get_database_backend(cls).get(object_id)

    def to_dict(self):
        fields = self.FIELDS + self._DEFAULT_FIELDS
        values = attrgetter(*fields)(self)
        return dict(zip(fields, values))


class CreationTimeModel(Model):
    _DEFAULT_FIELDS = Model._DEFAULT_FIELDS + ['creation_time']

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        if self.creation_time is None:
            self.creation_time = datetime_to_integer(datetime.utcnow())


class DatabaseBackendBase():

    def __init__(self, model_class):
        self.model_class = model_class

    def _raise_does_not_exist(self, object_id):
        raise DatabaseObjectNotFound(f'{self.model_class.__name__} object not found: {object_id}')

    def save(self, object):
        raise NotImplementedError

    def get(self, object_id):
        raise NotImplementedError

    def delete(self, object_id):
        raise NotImplementedError


class DummyDatabaseBackend(DatabaseBackendBase):
    """
    Dummy database backend that stores data in local memory.
    Suitable for unit tests. Do not use in production.
    """

    DB = {}

    def __init__(self, model_class):
        super().__init__(model_class)
        self.DB[model_class] = {}

    def save(self, object):
        self.DB[self.model_class][object.id] = object.to_dict()

    def get(self, object_id):
        keys_values = self.DB[self.model_class].get(object_id)
        if keys_values is None:
            self._raise_does_not_exist(object_id)
        return self.model_class(**keys_values)

    def delete(self, object_id):
        del self.DB[self.model_class][object_id]


class CloudDatastoreDatabaseBackend(DatabaseBackendBase):
    """
    Database backend for Cloud Datastore.
    """

    def __init__(self, model_class):
        super().__init__(model_class)
        self.kind = model_class.__name__
        self.client = datastore.Client()

    def save(self, object):
        key = self.client.key(self.kind, object.id)
        entity = datastore.Entity(key=key)
        entity.update(object.to_dict())
        self.client.put(entity)

    def get(self, object_id):
        key = self.client.key(self.kind, object_id)
        entity = self.client.get(key)
        if entity is None:
            self._raise_does_not_exist(object_id)
        return self.model_class(**entity)

    def delete(self, object_id):
        key = self.client.key(self.kind, object_id)
        self.client.delete(key)


def get_database_backend(model_class):
    if _database_backends.get(model_class) is None:
        klass = import_module_from_string(settings.DATABASE_BACKEND)
        _database_backends[model_class] = klass(model_class)
    return _database_backends[model_class]


_database_backends = {}