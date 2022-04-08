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

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            session.resolveLatestMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null);
            session.resolveLatestMavenArtifact("org.wildfly.core", "wildfly.core.cli", null, null);
            session.resolveLatestMavenArtifact("io.undertow", "undertow-core", null, null);
            session.resolveLatestMavenArtifact("io.undertow", "undertow-servlet", null, null);
            // This should not be recorded, size should remain 4.
            session.resolveLatestMavenArtifact("io.undertow", "undertow-servlet", null, null);

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
