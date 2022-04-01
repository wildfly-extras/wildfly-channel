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
package org.wildfly.channel.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.ChannelRequirement;

public class ChannelRequirementTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static ChannelRequirement from(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, ChannelRequirement.class);
    }

    @Test
    public void testValidRequires() throws IOException {
        ChannelRequirement requirement = from("groupId: org.foo.channels\n" +
                "artifactId: my-other-channel");

        assertEquals("org.foo.channels", requirement.getGroupId());
        assertEquals("my-other-channel", requirement.getArtifactId());
        assertNull(requirement.getVersion());

        System.out.println("requirement = " + requirement);
    }

    @Test
    public void testValidRequiresWithVersion() throws IOException {
        ChannelRequirement requirement = from("groupId: org.foo.channels\n" +
                "artifactId: my-other-channel\n" +
                "version: 1.2.3.Final");

        assertEquals("org.foo.channels", requirement.getGroupId());
        assertEquals("my-other-channel", requirement.getArtifactId());
        assertEquals("1.2.3.Final", requirement.getVersion());

        System.out.println("requirement = " + requirement);
    }

    @Test
    public void testInvalidRequires() {
        Assertions.assertThrows(Exception.class, () -> {
            // missing artifactID
            from("groupId: org.foo.channels");
        });

        Assertions.assertThrows(Exception.class, () -> {
            // missing groupId
            from("artifactId: my-other-channel");
        });
    }
}
