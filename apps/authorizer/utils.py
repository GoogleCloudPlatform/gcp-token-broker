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


from conf import settings


def datetime_to_integer(x):
    return int(x.timestamp() * 1000)


def import_module_from_string(path):
    module_name, klass = path.rsplit('.', 1)
    module = __import__(module_name, fromlist=[klass])
    return getattr(module, klass)


def validate_domain(user):
    try:
        username, domain = user.split('@')
        assert domain == settings.DOMAIN_NAME
    except (ValueError, AssertionError):
        raise ValueError("Invalid domain")