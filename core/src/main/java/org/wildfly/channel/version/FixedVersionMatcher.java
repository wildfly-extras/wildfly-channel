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

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.Set;

public class FixedVersionMatcher implements VersionMatcher {

    private final String version;

    public FixedVersionMatcher(String version) {
        requireNonNull(version);
        this.version = version;
    }

    @Override
    public Optional<String> matches(Set<String> samples) {
        requireNonNull(samples);
        if (samples.contains(version)) {
            return Optional.of(version);
        }
        return Optional.empty();
    }
}
