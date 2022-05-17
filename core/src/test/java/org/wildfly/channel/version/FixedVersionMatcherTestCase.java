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

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class FixedVersionMatcherTestCase {

    public Optional<String> matches(String version, Set<String> samples) {
        VersionMatcher matcher = new FixedVersionMatcher(version);
        return matcher.matches(samples);
    }

    @Test
    public void testFixedVersionIsFound() {
        Set<String> samples = new HashSet<>(asList(
                "1.0.0.Final",
                "1.0.1.Final",
                "1.0.1.SP1",
                "1.1.0.Final",
                "1.0.2.Final",
                "1.1.0.SP1",
                "2.0.0.Beta1",
                "2.0.0.Final"));

        Optional<String> match = matches("2.0.0.Final", samples);
        assertTrue(match.isPresent());
        assertEquals("2.0.0.Final", match.get());

        match = matches("1.1.0.SP1", samples);
        assertTrue(match.isPresent());
        assertEquals("1.1.0.SP1", match.get());
    }

    @Test
    public void testFixedVersionIsNotFound() {
        Set<String> samples = new HashSet<>(asList(
                "1.0.0.Final",
                "1.0.1.Final",
                "1.0.1.SP1",
                "1.1.0.Final",
                "1.0.2.Final",
                "1.1.0.SP1",
                "2.0.0.Beta1",
                "2.0.0.Final"));

        Optional<String> match = matches("3.0.0.Final", samples);
        assertFalse(match.isPresent());

        match = matches("1.1.0.SP2", samples);
        assertFalse(match.isPresent());
    }
}
