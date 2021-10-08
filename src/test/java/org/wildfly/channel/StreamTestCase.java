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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StreamTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static Stream from(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, Stream.class);
    }

    @Test
    public void testValidStream() throws IOException {
        Stream stream = from("groupId: org.wildfly\n" +
                "artifactId: wildfly-ee-galleon-pack\n" +
                "version: 26.0.0.Final\n" +
                "resolve-with-local-cache: true");
        assertEquals("org.wildfly", stream.getGroupId());
        assertEquals("wildfly-ee-galleon-pack", stream.getArtifactId());
        assertEquals("26.0.0.Final", stream.getVersion());
        assertTrue(stream.isResolveWithLocalCache());
    }

    @Test
    public void testGroupIdIsMandatory() {
        Assertions.assertThrows(Exception.class, () -> {
            from("artifactId: wildfly-ee-galleon-pack\n" +
                    "version: 26.0.0.Final");
        });
    }

    @Test
    public void testArtifactIdIsMandatory() {
        Assertions.assertThrows(Exception.class, () -> {
            from("groupId: org.wildfly\n" +
                    "version: 26.0.0.Final");
        });
    }

    @Test
    public void testVersionIsOptional() throws IOException {
        Stream stream = from("groupId: org.wildfly\n" +
                "artifactId: wildfly-ee-galleon-pack");
        assertNull(stream.getVersion());
    }

    @Test
    public void testResolveWithLocalCacheIsOptional() throws IOException {
        Stream stream = from("groupId: org.wildfly\n" +
                "artifactId: wildfly-ee-galleon-pack");
        assertFalse(stream.isResolveWithLocalCache());
    }
}
