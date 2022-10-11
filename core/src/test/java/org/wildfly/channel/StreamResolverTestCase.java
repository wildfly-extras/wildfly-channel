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
package org.wildfly.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.channel.ChannelManifestMapper.CURRENT_SCHEMA_VERSION;

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class StreamResolverTestCase {

    @Test
    public void testFindingStreamMatchingArtifactIdAndGroupId() {

        String yamlContent = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    version: 3.0.0.Final\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: undertow-core\n" +
                "    version: 3.0.1.Final\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: undertow-servlet\n" +
                "    version: 3.0.2.Final";
        ChannelManifest manifest = ChannelManifestMapper.fromString(yamlContent);

        Optional<Stream> stream = manifest.findStreamFor("io.undertow", "undertow-core");
        assertTrue(stream.isPresent());
        assertEquals("io.undertow", stream.get().getGroupId());
        assertEquals("undertow-core", stream.get().getArtifactId());

        stream = manifest.findStreamFor("io.undertow", "undertow-servlet");
        assertTrue(stream.isPresent());
        assertEquals("io.undertow", stream.get().getGroupId());
        assertEquals("undertow-servlet", stream.get().getArtifactId());

        stream = manifest.findStreamFor("io.undertow", "undertow-websockets-jsr");
        assertTrue(stream.isPresent());
        assertEquals("io.undertow", stream.get().getGroupId());
        assertEquals("*", stream.get().getArtifactId());

        stream = manifest.findStreamFor("org.example", "foo");
        assertFalse(stream.isPresent());

    }
}
