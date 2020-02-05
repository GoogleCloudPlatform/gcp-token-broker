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

package com.google.cloud.broker.usermapping;

import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.typesafe.config.ConfigFactory;

import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;


public class KerberosUserMapperTest {

    private static SettingsOverride backupSettings;

    @BeforeClass
    public static void setUpClass() {
        Object rules = ConfigFactory.parseString(
        "rules=[" +
                // Short names (no realms):
                "{" +
                    "if: \"principal.realm == null and principal.primary.endsWith('-hello')\"," +
                    "then: \"principal.primary[:-6] + '-bonjour@altostrat.net'\"" +
                "}," +
                "{" +
                    "if: \"principal.realm == null and principal.primary.endsWith('-lowercase')\"," +
                    "then: \"principal.primary|lower + '@altostrat.com.au'\"" +
                "}," +
                "{" +
                    "if: \"principal.realm == null\"," +
                    "then: \"principal.primary + '@altostrat.com'\"" +
                "}," +
                // Kerberos usernames:
                "{" +
                    "if: \"principal.instance == null and principal.realm == 'EXAMPLE.COM'\"," +
                    "then: \"principal.primary + '@altostrat.com'\"" +
                "}," +
                "{" +
                    "if: \"principal.instance != null and principal.realm == 'EXAMPLE.COM'\"," +
                    "then: \"principal.primary + '--' + principal.instance + '@altostrat.com'\"" +
                "}," +
                "{" +
                    "if: \"principal.primary.endsWith('-app') and principal.realm == 'FOO.ORG'\"," +
                    "then: \"'robot-' + principal.primary[:-4] + '@altostrat.org'\"" +
                "}," +
                "{" +
                    "if: \"principal.realm == 'FOO.ORG'\"," +
                    "then: \"principal.primary + '@altostrat.org'\"" +
                "}" +
            "]"
        ).getAnyRef("rules");
        // Override settings
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.USER_MAPPING_RULES, rules
        ));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // Restore settings
        backupSettings.restore();
    }

    @Test
    public void testMapKerberosName() {
        KerberosUserMapper mapper = new KerberosUserMapper();
        assertEquals("alice@altostrat.com", mapper.map("alice@EXAMPLE.COM"));
        assertEquals("hive--example.com@altostrat.com", mapper.map("hive/example.com@EXAMPLE.COM"));
        assertEquals("robot-yarn@altostrat.org", mapper.map("yarn-app@FOO.ORG"));
        assertEquals("bob@altostrat.org", mapper.map("bob@FOO.ORG"));
    }

    @Test
    public void testMapShortName() {
        KerberosUserMapper mapper = new KerberosUserMapper();
        assertEquals("alice@altostrat.com", mapper.map("alice"));
        assertEquals("john-bonjour@altostrat.net", mapper.map("john-hello"));
        assertEquals("marie-lowercase@altostrat.com.au", mapper.map("MaRiE-lowercase"));
    }

    @Test
    public void testUnmappableKerberosNames() {
        KerberosUserMapper mapper = new KerberosUserMapper();
        try {
            mapper.map("alice@BLAH.NET");
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            mapper.map("@EXAMPLE.COM");
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            mapper.map("@");
            fail();
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testUndefinedVariableInIfCondition() throws Exception {
        Object rules = ConfigFactory.parseString(
        "rules=[" +
                "{" +
                    "if: \"foo\"," +
                    "then: \"'bar@baz'\"" +  // Undefined variable
                "}," +
            "]"
        ).getAnyRef("rules");

        try(SettingsOverride override = new SettingsOverride(Map.of(
            AppSettings.USER_MAPPING_RULES, rules
        ))) {
            try {
                new KerberosUserMapper();
                fail();
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Unknown token found: foo"));
            }
        }
    }

    @Test
    public void testUndefinedVariableInThenExpression() throws Exception {
        Object rules = ConfigFactory.parseString(
        "rules=[" +
                "{" +
                    "if: \"principal.realm == 'FOO'\"," +
                    "then: \"bar\"" +  // Undefined variable
                "}," +
            "]"
        ).getAnyRef("rules");

        try(SettingsOverride override = new SettingsOverride(Map.of(
            AppSettings.USER_MAPPING_RULES, rules
        ))) {
            try {
                new KerberosUserMapper();
                fail();
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Unknown token found: bar"));
            }
        }
    }

    @Test
    public void testInvalidSyntaxInIfCondition() throws Exception {
        Object rules = ConfigFactory.parseString(
        "rules=[" +
                "{" +
                    "if: \"((;=\"," +  // Syntax error
                    "then: \"principal.primary + '@foo.bar'\"" +
                "}," +
            "]"
        ).getAnyRef("rules");

        try(SettingsOverride override = new SettingsOverride(Map.of(
            AppSettings.USER_MAPPING_RULES, rules
        ))) {
            try {
                new KerberosUserMapper();
                fail();
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Invalid expression: ((;="));
            }
        }
    }

    @Test
    public void testInvalidSyntaxInThenExpression() throws Exception {
        Object rules = ConfigFactory.parseString(
        "rules=[" +
                "{" +
                    "if: \"principal.realm == 'FOO'\"," +
                    "then: \"****\"" +   // Syntax error
                "}," +
            "]"
        ).getAnyRef("rules");

        try(SettingsOverride override = new SettingsOverride(Map.of(
            AppSettings.USER_MAPPING_RULES, rules
        ))) {
            try {
                new KerberosUserMapper();
                fail();
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Invalid expression: ****"));
            }
        }
    }

}