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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
        assertNull(rule.getQualifierPattern());

        rule = from("stream: minor");
        assertEquals(MINOR, rule.getStream());
        assertNull(rule.getQualifierPattern());

        rule = from("stream: micro");
        assertEquals(MICRO, rule.getStream());
        assertNull(rule.getQualifierPattern());

        rule = from("stream: qualifier");
        assertEquals(QUALIFIER, rule.getStream());
        assertNull(rule.getQualifierPattern());
    }

    @Test
    public void testMappingWithMissingStream() {
        Assertions.assertThrows(Exception.class, () -> {
            from("qualifierPattern: \\.*");
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
                "qualifierPattern: Final-jbossorg-\\d+");
        assertEquals(MAJOR, rule.getStream());
        assertNotNull(rule.getQualifierPattern());
        assertEquals("Final-jbossorg-\\d+", rule.getQualifierPattern().pattern());
    }

    @Test
    public void testVersionRule() {
        Set<String> samples = new HashSet<>(Arrays.asList(
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
        assertMatches("2.0.1.Final", "1.0.0.Final", MAJOR, null, samples);
        assertMatches("2.0.0.SP1", "1.0.0.Final", MAJOR, compile("SP1"), samples);

        assertMatches("1.4.1.Final", "1.0.0.Final", MINOR, null, samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.0.0.Final", MINOR, compile("Final-jbossorg-\\d+"), samples);

        assertMatches(null, "1.0.0.Final", MICRO, null, samples);
        assertMatches("1.2.3.SP1", "1.2.0.Final", MICRO, null, samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.2.0.Final", MICRO, compile("Final.*"), samples);

        assertMatches("1.2.3.SP1", "1.2.3.Final", QUALIFIER, null, samples);
        assertMatches("1.2.3.Final", "1.2.3.Final", QUALIFIER, compile("Final"), samples);
        assertMatches("1.2.3.Final-jbossorg-00001", "1.2.3.Final", QUALIFIER, compile("Final.*"), samples);

    }

    private void assertMatches(String expectedVersion, String baseVersion, VersionRule.VersionStream versionStream, Pattern qualifierPattern, Set<String> samples) {
        VersionRule rule = new VersionRule(versionStream, qualifierPattern);

        Optional<String> found = rule.matches(baseVersion, samples);
        if (expectedVersion == null && !found.isPresent()) {
            // OK!
            return;
        }
        assertTrue(found.isPresent());
        assertEquals(expectedVersion, found.get());
    }
}
