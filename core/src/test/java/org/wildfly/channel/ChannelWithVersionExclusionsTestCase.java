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

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelMapper.CURRENT_SCHEMA_VERSION;

public class ChannelWithVersionExclusionsTestCase {

    @Test
    public void testFindLatestMavenArtifactVersion() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'\n" +
                "    excludedVersions:\n" +
                "      - '25.0.1.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionExcludesAllVersionsException() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'\n" +
                "    excludedVersions:\n" +
                "      - '25.0.1.Final'\n" +
                "      - '25.0.0.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.0.Final", "25.0.1.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            try {
                session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "26.0.0.Final");
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifact() throws Exception {
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'\n" +
                "    excludedVersions:\n" +
                "      - '25.0.1.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.0.Final", "25.0.1.Final", "25.0.2.Final")));
        when(resolver.resolveArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.2.Final")).thenReturn(resolvedArtifactFile);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            MavenArtifact artifact = session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertNotNull(artifact);

            assertEquals("org.wildfly", artifact.getGroupId());
            assertEquals("wildfly-ee-galleon-pack", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("25.0.2.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testChannelWithVersionAndExclusion() throws Exception {
        Assertions.assertThrows(InvalidChannelException.class, ()->
            ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    version: '25.0.0.Final'\n" +
                        "    excludedVersions:\n" +
                        "      - '25.0.1.Final'")
        );

    }
}
