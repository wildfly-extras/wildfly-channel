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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class StreamResolverTestCase {

    @Test
    public void testFindingStreamMatchingArtifactIdAndGroupId() {

        String yamlContent = "streams:\n" +
                "  - groupId: '*'\n" +
                "    artifactId: '*'\n" +
                "    version: 3.0.Final\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    version: 3.0.Final\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: undertow-core\n" +
                "    version: 3.0.Final\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: undertow-servlet\n" +
                "    version: 3.0.Final";
                Channel channel = ChannelMapper.fromString(yamlContent).get(0);

        Optional<Stream> stream = channel.findStreamFor("io.undertow", "undertow-core");
        assertTrue(stream.isPresent());
        assertEquals("io.undertow", stream.get().getGroupId());
        assertEquals("undertow-core", stream.get().getArtifactId());

        stream = channel.findStreamFor("io.undertow", "undertow-servlet");
        assertTrue(stream.isPresent());
        assertEquals("io.undertow", stream.get().getGroupId());
        assertEquals("undertow-servlet", stream.get().getArtifactId());

        stream = channel.findStreamFor("io.undertow", "undertow-websockets-jsr");
        assertTrue(stream.isPresent());
        assertEquals("io.undertow", stream.get().getGroupId());
        assertEquals("*", stream.get().getArtifactId());

        stream = channel.findStreamFor("org.example", "foo");
        assertTrue(stream.isPresent());
        assertEquals("*", stream.get().getGroupId());
        assertEquals("*", stream.get().getArtifactId());

    }
}
