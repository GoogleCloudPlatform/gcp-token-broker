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

import com.google.cloud.broker.validation.EmailValidation;

public class MapUser {

    public static void main(String[] args) {
        if (args.length == 1) {
            String email = AbstractUserMapper.getInstance().map(args[0]);
            EmailValidation.validateEmail(email);
            System.out.println(email);
        }
        else {
            System.err.println("This command requires one argument.");
            System.exit(1);
        }

    }

}