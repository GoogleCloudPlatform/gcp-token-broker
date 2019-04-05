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

import os
import importlib

import default_settings


class Settings:
    def __init__(self):
        for setting in dir(default_settings):
            if setting.isupper():
                default_value = getattr(default_settings, setting)

                # Override default setting with potential environment variables
                value = os.environ.get(f'APP_SETTING_{setting}', default_value)
                setattr(self, setting, value)


settings = Settings()