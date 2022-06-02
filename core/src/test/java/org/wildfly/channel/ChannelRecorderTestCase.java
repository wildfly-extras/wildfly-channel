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

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelRecorderTestCase {
    @Test
    public void testChannelRecorder() throws IOException, UnresolvedMavenArtifactException {

        List<Channel> channels = ChannelMapper.fromString("---\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '24\\.\\d+\\.\\d+.Final'\n" +
                "  - groupId: org.wildfly.core\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '18\\.\\d+\\.\\d+.Final'\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '2\\.\\1\\.\\d+.Final'\n" +
                "---\n" +
                "streams:\n" +
                "  - groupId: io.undertow\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '2\\.\\d+\\.\\d+.Final'");
        Assertions.assertNotNull(channels);
        assertEquals(2, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create())
                .thenReturn(resolver);
        when(resolver.getAllVersions(eq("org.wildfly"), anyString(), eq(null), eq(null)))
                .thenReturn(singleton("24.0.0.Final"));
        when(resolver.getAllVersions(eq("org.wildfly.core"), anyString(), eq(null), eq(null)))
                .thenReturn(singleton("18.0.0.Final"));
        when(resolver.getAllVersions(eq("io.undertow"), anyString(), eq(null), eq(null)))
                .thenReturn(Set.of("2.1.0.Final", "2.2.0.Final"));
        when(resolver.resolveArtifact(anyString(), anyString(), eq(null), eq(null), anyString()))
                .thenReturn(mock(File.class));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "20.0.0.Final");
            session.resolveMavenArtifact("org.wildfly.core", "wildfly.core.cli", null, null, "15.0.0.Final");
            session.resolveMavenArtifact("io.undertow", "undertow-core", null, null, "1.0.0.Final");
            session.resolveMavenArtifact("io.undertow", "undertow-servlet", null, null, "1.0.0.Final");
            // This should not be recorded, size should remain 4.
            session.resolveMavenArtifact("io.undertow", "undertow-servlet", null, null, "1.0.0.Final");

            Channel recordedChannel = session.getRecordedChannel();
            System.out.println(ChannelMapper.toYaml(recordedChannel));

            Collection<Stream> streams = recordedChannel.getStreams();

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

    @Test
    public void testChannelRecorderWithMultipleVersions() throws IOException, UnresolvedMavenArtifactException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create())
                .thenReturn(resolver);
        when(resolver.getAllVersions(eq("io.undertow"), anyString(), eq(null), eq(null)))
                .thenReturn(Set.of("1.0.0.Final", "1.1.1.Final", "2.0.0.Final", "2.1.0.Final", "2.2.0.Final"));
        when(resolver.resolveArtifact(anyString(), anyString(), eq(null), eq(null), anyString()))
                .thenReturn(mock(File.class));

        try (ChannelSession session = new ChannelSession(emptyList(), factory)) {
            session.resolveDirectMavenArtifact("io.undertow", "undertow-core", null, null, "1.0.0.Final");
            session.resolveDirectMavenArtifact("io.undertow", "undertow-core", null, null, "1.0.0.Final");
            session.resolveDirectMavenArtifact("io.undertow", "undertow-core", null, null, "2.0.0.Final");

            Channel recordedChannel = session.getRecordedChannel();
            System.out.println(ChannelMapper.toYaml(recordedChannel));

            Collection<Stream> streams = recordedChannel.getStreams();
            assertEquals(1, streams.size());
            Stream stream = streams.iterator().next();
            assertEquals("io.undertow", stream.getGroupId());
            assertEquals("undertow-core", stream.getArtifactId());
            assertNull(stream.getVersion());
            assertNull(stream.getVersionPattern());
            Map<String, Pattern> versions = stream.getVersions();
            assertNotNull(versions);
            assertEquals(2, versions.size());
            assertTrue(versions.containsKey(Pattern.quote("1.0.0.Final")));
            assertTrue(versions.containsKey(Pattern.quote("2.0.0.Final")));


            // let's test the recorded channel serialization
            List<Channel> channels = ChannelMapper.fromString(ChannelMapper.toYaml(recordedChannel));
            assertEquals(1, channels.size());
            Channel readChannel = channels.get(0);

            try (ChannelSession session1 = new ChannelSession(List.of(readChannel), factory)) {
                MavenArtifact mavenArtifact_100Final = session1.resolveMavenArtifact("io.undertow", "undertow-core", null, null, "1.0.0.Final");
                assertEquals("1.0.0.Final", mavenArtifact_100Final.getVersion());

                MavenArtifact mavenArtifact_200Final = session1.resolveMavenArtifact("io.undertow", "undertow-core", null, null, "2.0.0.Final");
                assertEquals("2.0.0.Final", mavenArtifact_200Final.getVersion());
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
