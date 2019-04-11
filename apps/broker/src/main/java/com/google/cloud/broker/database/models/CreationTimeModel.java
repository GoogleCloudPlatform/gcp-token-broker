// Copyright 2019 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.database.models;

import java.util.HashMap;

public abstract class CreationTimeModel extends Model {

    public CreationTimeModel(HashMap<String, Object> values) {
        super(values);
        if (!this.values.containsKey("creation_time")) {
            this.values.put("creation_time", Long.valueOf(System.currentTimeMillis()));
        }
    }

}
