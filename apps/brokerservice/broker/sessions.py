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

import secrets
from datetime import datetime

from common.utils import datetime_to_integer
from common.conf import settings
from common.database import CreationTimeModel


class Session(CreationTimeModel):
    FIELDS = ['password', 'owner', 'renewer', 'expires_at', 'target', 'scope']

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        if self.password is None:
            self.password = secrets.token_urlsafe(24)
        if self.expires_at is None:
            self.extend_lifetime()

    def extend_lifetime(self):
        now = datetime_to_integer(datetime.utcnow())
        self.expires_at = now + min(
            settings.SESSION_RENEW_PERIOD,
            settings.SESSION_MAXIMUM_LIFETIME
        )

    def is_expired(self):
        now = datetime.utcnow()
        return datetime_to_integer(now) >= self.expires_at