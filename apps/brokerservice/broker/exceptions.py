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

import grpc


class BrokerGRPCException(Exception):

    def __init__(self, code, message=''):
        self.code = code
        self.message = message


def abort(code, message=''):
    raise BrokerGRPCException(code, message=message)


# TODO: Handle the case when an unhandled exception happens in
# the "handled_error" and "unhandled_error" methods themselves.

def handled_error(exception, traceback, endpoint, request, context):
    from broker.logging import get_logger
    from broker.utils import enrich_log_data
    log_data = {
        'responseType': 'reject',
        'responseCode': exception.code.value[0],
        'responseMessage': f'{exception.code.value[1]}: {exception.message}'
    }
    enrich_log_data(log_data, request, context)
    get_logger().info(endpoint.__name__, extra=log_data)
    # It's a voluntary exception, so include the details in the response.
    context.abort(exception.code, exception.message)


def unhandled_error(exception, traceback, endpoint, request, context):
    from broker.logging import get_logger
    from broker.utils import enrich_log_data
    code = grpc.StatusCode.UNKNOWN
    message = 'Server error'
    log_data = {
        'responseType': 'server-error',
        'responseCode': code.value[0],
        'responseMessage': message,
        'traceback': traceback.format_exc()
    }
    enrich_log_data(log_data, request, context)
    get_logger().error(endpoint.__name__, extra=log_data)
    # It's an unhandled exception, so mask the details from the response.
    context.abort(code, message)