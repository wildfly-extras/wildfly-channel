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

public class VersionPatternMatcherTestCase {

    @Test
    public void testVersionPattern() {
        Set<String> samples = new HashSet<>(asList(
                "1.0.0.Final",
                "1.0.1.Final",
                "1.0.1.SP1",
                "1.1.0.Final",
                "1.0.2.Final",
                "1.1.0.SP1",
                "2.0.0.Beta1",
                "2.0.0.Final"));

        Optional<String> match = matches(".*", samples);
        assertTrue(match.isPresent());
        assertEquals("2.0.0.Final", match.get());

        match = matches("1\\.0\\..*", samples);
        assertTrue(match.isPresent());
        assertEquals("1.0.2.Final", match.get());

        match = matches("2\\..\\..*", samples);
        assertTrue(match.isPresent());
        assertEquals("2.0.0.Final", match.get());

        match = matches("3\\..\\..*", samples);
        assertFalse(match.isPresent());

        match = matches("1\\.0\\..*.Final", samples);
        assertTrue(match.isPresent());
        assertEquals("1.0.2.Final", match.get());

        match = matches("1\\..\\..*.Final", samples);
        assertTrue(match.isPresent());
        assertEquals("1.1.0.Final", match.get());

        match = matches("1\\..\\..*", samples);
        assertTrue(match.isPresent());
        assertEquals("1.1.0.SP1", match.get());
    }

    @Test
    public void testVersionPattern_2() {
        Set<String> samples = new HashSet<>(asList(
                "1.0.0.Final-jbossorg-00001",
                "1.1.0.Final-jbossorg-00001",
                "1.1.0.Final-jbossorg-00002",
                "2.0.0.Final-jbossorg-00001",
                "2.1.0.Final-jbossorg-00001"));

        Optional<String> match = matches("1\\..\\..*Final-jbossorg-\\d+", samples);
        assertTrue(match.isPresent());
        assertEquals("1.1.0.Final-jbossorg-00002", match.get());

        match = matches("2\\..\\..*Final-jbossorg-\\d+", samples);
        assertTrue(match.isPresent());
        assertEquals("2.1.0.Final-jbossorg-00001", match.get());
    }

    @Test
    public void testVersionPattern_3() {
        Set<String> samples = new HashSet<>(asList(
                "1.0",
                "1.9",
                "1.10"));

        Optional<String> match = matches("1\\..*", samples);
        assertTrue(match.isPresent());
        assertEquals("1.10", match.get());
    }

    @Test
    public void version10isLargerThen1() {
        assertTrue(VersionMatcher.COMPARATOR.compare("1.0", "10.0") < 0);
        assertTrue(VersionMatcher.COMPARATOR.compare("1.1", "1.10") < 0);
    }

    @Test
    public void versionCompareTest() {
        assertTrue(VersionMatcher.COMPARATOR.compare("1.4.1.SP1-redhat-00001", "1.4.1.SP1-redhat-00002") < 0);
        assertTrue(VersionMatcher.COMPARATOR.compare("1.4.1.SP1-redhat-1", "1.4.1.SP1-redhat-00002") < 0);
        assertTrue(VersionMatcher.COMPARATOR.compare("1.4.1.SP1-redhat-00001", "1.4.1.SP1-redhat-2") < 0);
        assertTrue(VersionMatcher.COMPARATOR.compare("1.4.1.SP1-redhat-00002", "1.4.1.SP2-redhat-00001") < 0);
        assertTrue(VersionMatcher.COMPARATOR.compare("2.16.0.amq-720004-redhat-1", "2.16.0.redhat-1") < 0);
    }

    public Optional<String> matches(String pattern, Set<String> samples) {
        VersionPatternMatcher comparator = new VersionPatternMatcher(Pattern.compile(pattern));
        return comparator.matches(samples);
    }
}
