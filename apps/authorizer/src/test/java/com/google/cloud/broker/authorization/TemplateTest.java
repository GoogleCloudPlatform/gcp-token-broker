/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.broker.authorization;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertTrue;

public class TemplateTest {
    private static SoySauce soySauce;

    @BeforeClass
    public static void setup() {
        SoyFileSet sfs = SoyFileSet.builder()
            .add(Resources.getResource("callback.soy"))
            .build();
        soySauce = sfs.compileTemplates();
    }

    @Test
    public void testRenderTemplate() {
        Map<String, Object> data = ImmutableMap.<String, Object>builder()
            .put("principal", "user@EXAMPLE.COM")
            .put("email", "user@example.com")
            .put("picture", "https://cdn.example.com/user.jpg")
            .build();
        String content = soySauce
            .renderTemplate("Authorizer.Templates.Callback.success")
            .setData(data)
            .renderHtml()
            .get()
            .getContent();
        assertTrue(content.equals("<html><head><title>Token Broker Authorizer</title></head><body><h1>Refresh token saved</h1><div><p>Principal: user@EXAMPLE.COM</p><p>Email: user@example.com</p><p><img alt=\"profile\" src=\"https://cdn.example.com/user.jpg\"/></p></div></body></html>"));

    }
}
