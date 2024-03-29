/*
 * Copyright 2020 Google LLC
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

package com.google.cloud.broker.apps.authorizer;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class TemplateTest {
  private static SoySauce soySauce;

  @BeforeClass
  public static void setup() {
    SoyFileSet sfs =
        SoyFileSet.builder()
            .add(Resources.getResource("index.soy"))
            .add(Resources.getResource("success.soy"))
            .build();
    soySauce = sfs.compileTemplates();
  }

  @Test
  public void testIndex() {
    Map<String, Object> data =
        ImmutableMap.<String, Object>builder()
            .put("principal", "user@EXAMPLE.COM")
            .put("email", "user@example.com")
            .put("picture", "https://cdn.example.com/user.jpg")
            .build();
    String content =
        soySauce
            .renderTemplate("Authorizer.Templates.success")
            .setData(data)
            .renderHtml()
            .get()
            .getContent();
  }
}
