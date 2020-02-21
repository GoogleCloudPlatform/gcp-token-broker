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

package com.google.cloud.broker.database.backends;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.broker.database.models.Model;
import com.google.common.collect.ImmutableList;

public class Foo extends Model {

    private String id;
    private byte[] byteVal;
    private Long longVal;
    private List<String> stringList;

    public Foo(@JsonProperty("id") String id,
               @JsonProperty("byteVal") byte[] byteVal,
               @JsonProperty("longVal") Long longVal,
               @JsonProperty("longVal") List<String> stringList) {
        setId(id);
        setByteVal(byteVal);
        setLongVal(longVal);
        setStringList(stringList);
    }

    @Override
    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("id", id);
        map.put("byteVal", byteVal);
        map.put("longVal", longVal);
        map.put("stringList", stringList);
        return map;
    }

    public static Model fromMap(Map<String, Object> map) {
        return new Foo(
            (String) map.get("id"),
            (byte[]) map.get("byteVal"),
            (Long) map.get("longVal"),
            (List<String>) map.get("stringList")
        );
    }

    public void setDBId(String id) {
        setId(id);
    }

    public String getDBId() {
        return getId();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public byte[] getByteVal() {
        return byteVal;
    }

    public void setByteVal(byte[] byteVal) {
        this.byteVal = byteVal;
    }

    public Long getLongVal() {
        return longVal;
    }

    public void setLongVal(Long longVal) {
        this.longVal = longVal;
    }

    public List<String> getStringList() {
        return stringList;
    }

    public void setStringList(List<String> stringList) {
        this.stringList = ImmutableList.copyOf(stringList);
    }

}
