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

import sys
import logging as standard_logging
import socket
from copy import deepcopy
from pprint import pformat

from common.utils import import_module_from_string
from common.conf import settings

LOG_LEVEL = standard_logging.getLevelName(settings.LOGGING_LEVEL)


class LoggingBackendBase():

    def create_logger():
        raise NotImplementedError


class StructLogLoggingBackend():

    def create_logger(self):
        import structlog
        standard_logging.basicConfig(
            format="%(message)s",
            stream=sys.stdout,
            level=LOG_LEVEL,
        )
        structlog.configure(
            processors=[
                structlog.stdlib.filter_by_level,
                structlog.stdlib.add_logger_name,
                structlog.stdlib.add_log_level,
                structlog.stdlib.PositionalArgumentsFormatter(),
                structlog.processors.StackInfoRenderer(),
                structlog.processors.format_exc_info,
                structlog.processors.UnicodeDecoder(),
                structlog.processors.JSONRenderer()
            ],
            context_class=dict,
            logger_factory=structlog.stdlib.LoggerFactory(),
            wrapper_class=structlog.stdlib.BoundLogger,
            cache_logger_on_first_use=True,
        )
        logger = structlog.get_logger()
        return logger


def get_logger():
    global _logger
    if _logger is None:
        klass = import_module_from_string(settings.LOGGING_BACKEND)
        _logger = klass().create_logger()
    return _logger


_logger = None