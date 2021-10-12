/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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

    public Optional<String> matches(String pattern, Set<String> samples) {
        VersionPatternMatcher comparator = new VersionPatternMatcher(Pattern.compile(pattern));
        return comparator.matches(samples);
    }
}
