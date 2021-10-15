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
package org.wildfly.channel;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.replaceAll;
import static java.util.regex.Pattern.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.channel.VersionRule.VersionStream.MAJOR;
import static org.wildfly.channel.VersionRule.VersionStream.MICRO;
import static org.wildfly.channel.VersionRule.VersionStream.MINOR;
import static org.wildfly.channel.VersionRule.VersionStream.QUALIFIER;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionRuleTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static VersionRule from(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, VersionRule.class);
    }

    @Test
    public void testMappingWithValidStreamValue() throws IOException {
        VersionRule rule = from("stream: major");
        assertEquals(MAJOR, rule.getStream());
        assertTrue(rule.getQualifiers().isEmpty());

        rule = from("stream: minor");
        assertEquals(MINOR, rule.getStream());
        assertTrue(rule.getQualifiers().isEmpty());

        rule = from("stream: micro");
        assertEquals(MICRO, rule.getStream());
        assertTrue(rule.getQualifiers().isEmpty());

        rule = from("stream: qualifier");
        assertEquals(QUALIFIER, rule.getStream());
        assertTrue(rule.getQualifiers().isEmpty());
    }

    @Test
    public void testMappingWithMissingStream() {
        Assertions.assertThrows(Exception.class, () -> {
            from("# just a comment");
        });
    }

    @Test
    public void testMappingWithInvalidStreamValue() {
        Assertions.assertThrows(Exception.class, () -> {
            from("stream: not-a-valid-value");
        });
    }

    @Test
    public void testMappingWithQualifierPattern() throws IOException {
        VersionRule rule = from("stream: major\n"+
                "qualifiers:\n" +
                "  - Final\n" +
                "  - Final-jbossorg-\\d+");
        assertEquals(MAJOR, rule.getStream());
        assertEquals(2, rule.getQualifiers().size());
        assertEquals("Final", rule.getQualifiers().get(0).pattern());
        assertEquals("Final-jbossorg-\\d+", rule.getQualifiers().get(1).pattern());
    }

    @Test
    public void testVersionRule() {
        Set<String> samples = new HashSet<>(asList(
                "1.2.0.Final",
                "1.2.1.Final",
                "1.2.3.Final",
                "1.2.3.Final-jbossorg-00001",
                "1.2.3.SP1",
                "1.3.0.Final",
                "1.4.1.Final",
                "2.0.0.Alpha1",
                "2.0.0.Beta1",
                "2.0.0.Final",
                "2.0.0.SP1",
                "2.0.1.Final"
        ));
        assertMatches("2.0.1.Final", "1.0.0.Final", MAJOR, emptyList(), samples);
        assertMatches("2.0.0.SP1", "1.0.0.Final", MAJOR, asList("SP1"), samples);

        assertMatches("1.4.1.Final", "1.0.0.Final", MINOR, emptyList(), samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.0.0.Final", MINOR, asList("Final-jbossorg-\\d+"), samples);

        assertMatches(null, "1.0.0.Final", MICRO, emptyList(), samples);
        assertMatches("1.2.3.SP1", "1.2.0.Final", MICRO, emptyList(), samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.2.0.Final", MICRO, asList("Final.*"), samples);

        assertMatches("1.2.3.SP1", "1.2.3.Final", QUALIFIER, emptyList(), samples);
        assertMatches("1.2.3.Final", "1.2.3.Final", QUALIFIER, asList("Final"), samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.2.3.Final", QUALIFIER, asList("Final-jbossorg-\\d+"), samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.2.3.Final", QUALIFIER, asList("Final", "Final-jbossorg-\\d+"), samples);

    }

    private void assertMatches(String expectedVersion, String baseVersion, VersionRule.VersionStream versionStream, List<String> qualifiers, Set<String> samples) {
        List<Pattern> patterns = qualifiers.stream().map(Pattern::compile).collect(Collectors.toList());
        VersionRule rule = new VersionRule(versionStream, patterns);

        Optional<String> found = rule.matches(baseVersion, samples);
        if (expectedVersion == null && !found.isPresent()) {
            // OK!
            return;
        }
        assertTrue(found.isPresent());
        assertEquals(expectedVersion, found.get());
    }
}
