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
package org.wildfly.channel.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.ManifestRequirement;

public class ManifestRequirementTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    static ManifestRequirement from(String str) throws IOException {
        return OBJECT_MAPPER.readValue(str, ManifestRequirement.class);
    }

    @Test
    public void testValidRequires() throws IOException {
        ManifestRequirement requirement = from(
                "id: test\n" +
                "maven:\n" +
                "  groupId: org.foo.channels\n" +
                "  artifactId: my-other-channel");

        assertEquals("org.foo.channels", requirement.getGroupId());
        assertEquals("my-other-channel", requirement.getArtifactId());
        assertNull(requirement.getVersion());

        System.out.println("requirement = " + requirement);
    }

    @Test
    public void testValidRequiresWithVersion() throws IOException {
        ManifestRequirement requirement = from(
                "id: test\n" +
                "maven:\n" +
                "  groupId: org.foo.channels\n" +
                "  artifactId: my-other-channel\n" +
                "  version: 1.2.3.Final");

        assertEquals("org.foo.channels", requirement.getGroupId());
        assertEquals("my-other-channel", requirement.getArtifactId());
        assertEquals("1.2.3.Final", requirement.getVersion());

        System.out.println("requirement = " + requirement);
    }

    @Test
    public void testInvalidRequires() {
        Assertions.assertThrows(Exception.class, () -> {
            // missing artifactID
            from(
                    "id:test\n" +
                        "maven:\n" +
                        "  groupId: org.foo.channels");
        });

        Assertions.assertThrows(Exception.class, () -> {
            // missing groupId
            from(
                    "id:test\n" +
                        "maven:\n" +
                        "  artifactId: my-other-channel");
        });
    }
}
