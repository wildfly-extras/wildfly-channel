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

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelManifestMapper.CURRENT_SCHEMA_VERSION;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelRecorderTestCase {

    @TempDir
    private Path tempDir;

    @Test
    public void testChannelRecorder() throws Exception {

        String manifest1 = "---\n" +
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '24\\.\\d+\\.\\d+.Final'\n" +
                "  - groupId: org.wildfly.core\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '18\\.\\d+\\.\\d+.Final'\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '2\\.\\1\\.\\d+.Final'\n";

        String manifest2 = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '2\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        final List<Channel> channels = ChannelSessionTestCase.mockChannel(resolver, tempDir, manifest1, manifest2);

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.getAllVersions(eq("org.wildfly"), anyString(), eq(null), eq(null)))
                .thenReturn(singleton("24.0.0.Final"));
        when(resolver.getAllVersions(eq("org.wildfly.core"), anyString(), eq(null), eq(null)))
                .thenReturn(singleton("18.0.0.Final"));
        when(resolver.getAllVersions(eq("io.undertow"), anyString(), eq(null), eq(null)))
                .thenReturn(new HashSet<>(Arrays.asList("2.1.0.Final", "2.2.0.Final")));
        when(resolver.resolveArtifact(anyString(), anyString(), eq(null), eq(null), anyString()))
                .thenReturn(mock(File.class));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "20.0.0.Final");
            session.resolveMavenArtifact("org.wildfly.core", "wildfly.core.cli", null, null, "15.0.0.Final");
            session.resolveMavenArtifact("io.undertow", "undertow-core", null, null, "1.0.0.Final");
            session.resolveMavenArtifact("io.undertow", "undertow-servlet", null, null, "1.0.0.Final");
            // This should not be recorded, size should remain 4.
            session.resolveMavenArtifact("io.undertow", "undertow-servlet", null, null, "1.0.0.Final");

            ChannelManifest recordedManifest = session.getRecordedChannel();
            System.out.println(ChannelManifestMapper.toYaml(recordedManifest));

            Collection<Stream> streams = recordedManifest.getStreams();

            assertStreamExistsFor(streams, "org.wildfly", "wildfly-ee-galleon-pack", "24.0.0.Final");
            assertStreamExistsFor(streams, "org.wildfly.core", "wildfly.core.cli", "18.0.0.Final");
            assertStreamExistsFor(streams, "io.undertow", "undertow-core", "2.2.0.Final");
            assertStreamExistsFor(streams, "io.undertow", "undertow-servlet", "2.2.0.Final");

            // Check that streams are sorted
            assertEquals(4, streams.size());
            int i = 0;
            for (Stream stream : streams) {
                switch (i) {
                    case 0:
                        assertEquals("io.undertow", stream.getGroupId());
                        assertEquals("undertow-core", stream.getArtifactId());
                        break;
                    case 1:
                        assertEquals("io.undertow", stream.getGroupId());
                        assertEquals("undertow-servlet", stream.getArtifactId());
                        break;
                    case 2:
                        assertEquals("org.wildfly", stream.getGroupId());
                        assertEquals("wildfly-ee-galleon-pack", stream.getArtifactId());
                        break;
                    case 3:
                        assertEquals("org.wildfly.core", stream.getGroupId());
                        assertEquals("wildfly.core.cli", stream.getArtifactId());
                        break;
                    default:
                        fail();
                }
                i++;
            }
        }
    }

    private static void assertStreamExistsFor(Collection<Stream> streams, String groupId, String artifactId, String version) {
        Optional<Stream> stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) &&
                s.getArtifactId().equals(artifactId) &&
                s.getVersion().equals(version)).findFirst();
        assertTrue(stream.isPresent());
        assertEquals(groupId, stream.get().getGroupId());
        assertEquals(artifactId, stream.get().getArtifactId());
        assertEquals(version, stream.get().getVersion());
        assertNull(stream.get().getVersionPattern());

    }
}
