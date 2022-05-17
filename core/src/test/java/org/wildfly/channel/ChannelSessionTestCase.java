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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelSessionTestCase {

    @Test
    public void testFindLatestMavenArtifactVersion() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(singleton("25.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null);
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionThrowsUnresolvedMavenArtifactException() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(singleton("26.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            try {
                session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null);
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifact() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(Set.of("25.0.0.Final", "25.0.1.Final"));
        when(resolver.resolveArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.1.Final")).thenReturn(resolvedArtifactFile);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            MavenArtifact artifact = session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null);
            assertNotNull(artifact);

            assertEquals("org.wildfly", artifact.getGroupId());
            assertEquals("wildfly-ee-galleon-pack", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("25.0.1.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifactThrowUnresolvedMavenArtifactException() {
        List<Channel> channels = ChannelMapper.fromString("streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(singleton("26.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            try {
                session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null);
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveDirectMavenArtifact() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("streams:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: foo\n" +
                "    version: \"25.0.0.Final\"");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.resolveArtifact("org.bar", "bar", null, null, "1.0.0.Final")).thenReturn(resolvedArtifactFile);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            Assertions.assertThrows(UnresolvedMavenArtifactException.class, () -> {
                session.resolveMavenArtifact("org.bar", "bar", null, null);
            });

            MavenArtifact artifact = session.resolveDirectMavenArtifact("org.bar", "bar", null, null, "1.0.0.Final");
            assertNotNull(artifact);

            assertEquals("org.bar", artifact.getGroupId());
            assertEquals("bar", artifact.getArtifactId());
            assertEquals(resolvedArtifactFile, artifact.getFile());

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("1.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(2)).close();
    }

}
