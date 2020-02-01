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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.cloud.broker.settings.AppSettings;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateError;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public class KerberosUserMapper extends AbstractUserMapper {

    private List<Rule> rulesList = new ArrayList<>();
    private final static String INVALID_SETTING = "Invalid `" + AppSettings.USER_MAPPING_RULES + "` setting -- ";

    static class KerberosName {
        private final String primary;
        private final String instance;
        private final String realm;

        public KerberosName(String name) {
            Pattern parser = Pattern.compile("([^/@]+)(/([^/@]+))?(@([^/@]+))?");
            Matcher match = parser.matcher(name);
            if (match.matches()) {
                primary = match.group(1);
                instance = (match.group(3) == null) ? "" : match.group(3);
                realm = (match.group(5) == null) ? "" : match.group(5);
            } else {
                throw new IllegalArgumentException("Malformed Kerberos name: " + name);
            }
        }

        public String getPrimary() {
            return primary;
        }

        public String getInstance() {
            return instance;
        }

        public String getRealm() {
            return realm;
        }
    }

    public KerberosUserMapper() {
        loadMappingRules();
    }

    private static class Rule {
        private final String ifCondition;
        private final String then;

        private Rule(String ifCondition, String then) {
            this.ifCondition = ifCondition;
            this.then = then;
        }
    }

    private void loadMappingRules() {
        List<? extends Config> rules = AppSettings.getInstance().getConfigList(AppSettings.USER_MAPPING_RULES);
        for (Config rule : rules) {
            String ifCondition;
            String then;
            try {
                ifCondition = rule.getString("if");
                then = rule.getString("then");
            } catch(ConfigException.Missing e) {
                throw new IllegalArgumentException(INVALID_SETTING + e.getMessage());
            }
            try {
                validateExpression(ifCondition);
                validateExpression(then);
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException(INVALID_SETTING + e.getMessage());
            }
            rulesList.add(new Rule(ifCondition, then));
        }
    }

    private void validateExpression(String expression) {
        Jinjava jinjava = new Jinjava();
        Context context = new Context();
        KerberosName dummy = new KerberosName("abcd/1.2.3.4@REALM");
        context.put("principal", dummy);
        JinjavaConfig config = JinjavaConfig.newBuilder().withValidationMode(true).withFailOnUnknownTokens(true).build();
        JinjavaInterpreter interpreter = new JinjavaInterpreter(jinjava, context, config);
        String template = "{{ " + expression + " }}";
        interpreter.render(template);
        List<TemplateError> errors = interpreter.getErrors();
        if (errors.size() > 0) {
            StringBuilder message = new StringBuilder();
            for (TemplateError error: errors) {
                message.append(error.getMessage());
            }
            throw new IllegalArgumentException(String.format("Invalid expression: %s\n%s", expression, message));
        }
    }

    private boolean evaluateIfCondition(Rule rule, Context context) {
        JinjavaInterpreter interpreter = new JinjavaInterpreter(new Jinjava(), context, new JinjavaConfig());
        String template = "{% if " + rule.ifCondition + " %}true{% else %}false{% endif %}";
        String rendered = interpreter.render(template);
        return Boolean.parseBoolean(rendered);
    }

    private String evaluateThen(Rule rule, Context context) {
        JinjavaInterpreter interpreter = new JinjavaInterpreter(new Jinjava(), context, new JinjavaConfig());
        String template = "{{ " + rule.then + " }}";
        return interpreter.render(template);
    }

    @Override
    public String map(String name) {
        Context context = new Context();
        context.put("principal", new KerberosName(name));
        // Look through the list of rules
        for (Rule rule : rulesList) {
            boolean isApplicable = evaluateIfCondition(rule, context);
            if (isApplicable) {
                // An applicable rule was found. Apply it to get the user mapping.
                return evaluateThen(rule, context);
            }
        }
        throw new IllegalArgumentException("Principal `" + name + "` cannot be mapped to a Google identity.");
    }

}
