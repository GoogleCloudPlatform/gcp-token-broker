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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.cloud.broker.settings.AppSettings;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.TemplateError;
import com.hubspot.jinjava.interpret.UnknownTokenException;
import com.hubspot.jinjava.util.ObjectTruthValue;
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
                instance = match.group(3);
                realm = match.group(5);
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

        public void validate() {
            // Create dummy context for the validation
            Jinjava jinjava = new Jinjava();
            Context context = new Context();
            KerberosName dummy = new KerberosName("abcd/1.2.3.4@REALM");
            context.put("principal", dummy);
            JinjavaConfig config = JinjavaConfig.newBuilder()
                .withValidationMode(true)
                .withFailOnUnknownTokens(true)
                .build();

            // Validate the `if` condition
            JinjavaInterpreter interpreter = new JinjavaInterpreter(jinjava, context, config);
            try {
                ObjectTruthValue.evaluate(interpreter.resolveELExpression(ifCondition, 0));
            }
            catch (UnknownTokenException e) {
                throw new IllegalArgumentException(String.format("Invalid expression: %s\n%s", ifCondition, e.getMessage()));
            }
            checkForSyntaxErrors(interpreter, ifCondition);

            // Validate the `then` expression
            interpreter = new JinjavaInterpreter(jinjava, context, config);
            try {
                interpreter.resolveELExpression(then, 0);
            }
            catch (UnknownTokenException e) {
                throw new IllegalArgumentException(String.format("Invalid expression: %s\n%s", then, e.getMessage()));
            }
            checkForSyntaxErrors(interpreter, then);
        }

        private static void checkForSyntaxErrors(JinjavaInterpreter interpreter, String expression) {
            List<TemplateError> errors = interpreter.getErrors();
            if (errors.size() > 0) {
                StringBuilder message = new StringBuilder();
                for (TemplateError error: errors) {
                    message.append(error.getMessage());
                }
                throw new IllegalArgumentException(String.format("Invalid expression: %s\n%s", expression, message));
            }
        }

        public boolean evaluateIfCondition(Context context) {
            JinjavaInterpreter interpreter = new JinjavaInterpreter(new Jinjava(), context, new JinjavaConfig());
            return ObjectTruthValue.evaluate(interpreter.resolveELExpression(ifCondition, 0));
        }

        public String evaluateThenExpression(Context context) {
            JinjavaInterpreter interpreter = new JinjavaInterpreter(new Jinjava(), context, new JinjavaConfig());
            Object rendered = interpreter.resolveELExpression(then, 0);
            return Objects.toString(rendered, "");
        }

    }

    private void loadMappingRules() {
        List<? extends Config> rules = AppSettings.getInstance().getConfigList(AppSettings.USER_MAPPING_RULES);
        for (Config ruleConfig : rules) {
            String ifCondition;
            String then;
            try {
                ifCondition = ruleConfig.getString("if");
                then = ruleConfig.getString("then");
            } catch(ConfigException.Missing e) {
                throw new IllegalArgumentException(INVALID_SETTING + e.getMessage());
            }
            Rule rule = new Rule(ifCondition, then);
            try {
                rule.validate();
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException(INVALID_SETTING + e.getMessage());
            }
            rulesList.add(rule);
        }
    }

    @Override
    public String map(String name) {
        Context context = new Context();
        context.put("principal", new KerberosName(name));
        // Look through the list of rules
        for (Rule rule : rulesList) {
            boolean isApplicable = rule.evaluateIfCondition(context);
            if (isApplicable) {
                // An applicable rule was found. Apply it to get the user mapping.
                return rule.evaluateThenExpression(context);
            }
        }
        throw new IllegalArgumentException("Principal `" + name + "` cannot be mapped to a Google identity.");
    }

}
