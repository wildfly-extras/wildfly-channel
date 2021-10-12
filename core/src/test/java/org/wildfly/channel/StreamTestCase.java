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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

public class StreamTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static Stream fromYamlContent(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, Stream.class);
    }

    static Stream fromURL(URL url) throws IOException {
        return OBJECT_MAPPER.readValue(url, Stream.class);
    }

    @Test
    public void testValidStream() throws IOException {
        Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                "artifactId: wildfly-ee-galleon-pack\n" +
                "version: 26.0.0.Final");
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("wildfly-ee-galleon-pack", stream.getArtifactId());
        assertEquals("26.0.0.Final", stream.getVersion());
    }

    @Test
    public void testValidStreamWithVersionPattern() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("streams/stream-with-version-pattern.yaml");
        Stream stream = fromURL(file);
        assertEquals("org.example", stream.getGroupId());
        assertEquals("foo", stream.getArtifactId());
        assertNull(stream.getVersion());
        assertEquals("2\\.2\\..*", stream.getVersionPattern().pattern());
    }

    @Test
    public void testAnyGroupIdAndAnyArtifactIdStream() throws IOException {
        Stream stream = fromYamlContent("groupId: '*'\n" +
                "artifactId: '*'\n" +
                "version: 1.1.1.Final");
        assertEquals("*", stream.getGroupId());
        assertEquals("*", stream.getArtifactId());
    }

    @Test
    public void testAnyArtifactIdStream() throws IOException {
        Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                "artifactId: '*'\n" +
                "version: 1.2.0.Final");
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("*", stream.getArtifactId());
    }

    @Test
    public void testAnyGroupIdWithAGivenArtifactIdIsNotValid() {
        assertThrows(Exception.class, () -> {
            Stream stream = fromYamlContent("groupId: '*'\n" +
                    "artifactId: my-artifact\n" +
                    "version: 1.2.0.Final");
        });
    }

    @Test
    public void testGroupIdIsMandatory() {
        assertThrows(Exception.class, () -> {
            fromYamlContent("artifactId: wildfly-ee-galleon-pack\n" +
                    "version: 26.0.0.Final");
        });
    }

    @Test
    public void testArtifactIdIsMandatory() {
        assertThrows(Exception.class, () -> {
            fromYamlContent("groupId: org.wildfly\n" +
                    "version: 26.0.0.Final");
        });
    }

    @Test
    public void testMissingVersionAndVersionPattern()  {
        assertThrows(Exception.class, () -> {
            Stream stream = fromYamlContent("groupId: org.wildfly\n" +
                    "artifactId: wildfly-ee-galleon-pack");
        });
    }
}
