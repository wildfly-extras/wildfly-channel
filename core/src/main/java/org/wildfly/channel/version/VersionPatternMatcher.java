/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.channel.version;

import static java.util.Optional.empty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class VersionPatternMatcher implements VersionMatcher {
    private final Pattern pattern;

    public VersionPatternMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public Optional<String> matches(Set<String> samples) {
        List<String> matches = new ArrayList<>();
        for (String sample : samples) {
            if (pattern.matcher(sample).matches()) {
                matches.add(sample);
            }
        }
        if (matches.isEmpty()) {
            return empty();
        }

        matches.sort(COMPARATOR);
        return Optional.of(matches.get(matches.size() - 1));
    }
}
