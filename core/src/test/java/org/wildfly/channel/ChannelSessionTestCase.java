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

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelMapper.CURRENT_SCHEMA_VERSION;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelSessionTestCase {

    @Test
    public void testFindLatestMavenArtifactVersion() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
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
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionThrowsUnresolvedMavenArtifactException() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString(                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
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
                session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "26.0.0.Final");
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifact() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
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

            MavenArtifact artifact = session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
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
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "schemaVersion: 1.0.0\n" +
                "streams:\n" +
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
                session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
                fail("Must throw a UnresolvedMavenArtifactException");
            } catch (UnresolvedMavenArtifactException e) {
                // pass
            }
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveDirectMavenArtifact() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
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
                session.resolveMavenArtifact("org.bar", "bar", null, null, "25.0.0.Final");
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

    @Test
    public void testResolveMavenArtifactsFromOneChannel() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.foo\n" +
                                                             "    artifactId: foo\n" +
                                                             "    version: \"25.0.0.Final\"\n" +
                                                             "  - groupId: org.bar\n" +
                                                             "    artifactId: bar\n" +
                                                             "    version: \"26.0.0.Final\""
                                                             );
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "foo", null, null, "1.0.0"),
           new ArtifactCoordinate("org.bar", "bar", null, null, "1.0.0"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
           .thenReturn(asList(resolvedArtifactFile1, resolvedArtifactFile2));

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveMavenArtifacts(coordinates);
            assertNotNull(resolved);

            final List<MavenArtifact> expected = asList(
               new MavenArtifact("org.foo", "foo", null, null, "25.0.0.Final", resolvedArtifactFile1),
               new MavenArtifact("org.bar", "bar", null, null, "26.0.0.Final", resolvedArtifactFile2)
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.foo", "foo");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveMavenArtifactsFromTwoChannel() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.foo\n" +
                                                             "    artifactId: foo\n" +
                                                             "    version: \"25.0.0.Final\"\n" +
                                                             "---\n" +
                                                             "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.bar\n" +
                                                             "    artifactId: bar\n" +
                                                             "    version: \"26.0.0.Final\""
        );
        assertNotNull(channels);
        assertEquals(2, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "foo", null, null, "1.0.0"),
           new ArtifactCoordinate("org.bar", "bar", null, null, "1.0.0"));
        when(resolver.resolveArtifacts(any()))
           .then(new Answer<List<File>>() {
               @Override
               public List<File> answer(InvocationOnMock invocationOnMock) throws Throwable {
                   final List<ArtifactCoordinate> coordinates = invocationOnMock.getArgument(0);
                   assertEquals(1, coordinates.size());
                   if (coordinates.get(0).getArtifactId().equals("foo")) {
                       return asList(resolvedArtifactFile1);
                   } else if (coordinates.get(0).getArtifactId().equals("bar")) {
                       return asList(resolvedArtifactFile2);
                   } else {
                       return null;
                   }
               }
           });

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveMavenArtifacts(coordinates);
            assertNotNull(resolved);

            final List<MavenArtifact> expected = asList(
               new MavenArtifact("org.foo", "foo", null, null, "25.0.0.Final", resolvedArtifactFile1),
               new MavenArtifact("org.bar", "bar", null, null, "26.0.0.Final", resolvedArtifactFile2)
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.foo", "foo");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(3)).close();
    }

    @Test
    public void testResolveDirectMavenArtifacts() throws UnresolvedMavenArtifactException {
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.not\n" +
                                                             "    artifactId: used\n" +
                                                             "    version: \"1.0.0.Final\"");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "foo", null, null, "25.0.0.Final"),
           new ArtifactCoordinate("org.bar", "bar", null, null, "26.0.0.Final"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
           .thenReturn(asList(resolvedArtifactFile1, resolvedArtifactFile2));

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveDirectMavenArtifacts(coordinates);
            assertNotNull(resolved);

            final List<MavenArtifact> expected = asList(
               new MavenArtifact("org.foo", "foo", null, null, "25.0.0.Final", resolvedArtifactFile1),
               new MavenArtifact("org.bar", "bar", null, null, "26.0.0.Final", resolvedArtifactFile2)
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.foo", "foo");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveMavenArtifactsFromTwoChannelsWithSameStream() throws UnresolvedMavenArtifactException {
        Channel channel1 = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: foo\n" +
                "    version: \"25.0.0.Final\""
        ).get(0);
        assertNotNull(channel1);
        Channel channel2 = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: foo\n" +
                "    version: \"26.0.0.Final\""
        ).get(0);
        assertNotNull(channel2);

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.resolveArtifact(eq("org.foo"), eq("foo"), eq(null), eq(null), anyString())).thenReturn(resolvedArtifactFile);

        // channel order does not matter to determine the latest version
        try (ChannelSession session = new ChannelSession(asList(channel1, channel2), factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "foo", null, null, "1.0.0.Final");
            assertNotNull(resolvedArtifact);
            assertEquals("26.0.0.Final", resolvedArtifact.getVersion());
        }
        try (ChannelSession session = new ChannelSession(asList(channel2, channel1), factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "foo", null, null, "1.0.0.Final");
            assertNotNull(resolvedArtifact);
            assertEquals("26.0.0.Final", resolvedArtifact.getVersion());
        }
    }

    @Test
    public void testResolveMavenArtifactFromChannelWithWildcardGroup() throws UnresolvedMavenArtifactException {
        Channel channel1 = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                       "streams:\n" +
                                                       "  - groupId: \"*\"\n" +
                                                       "    artifactId: \"*\"\n" +
                                                       "    version: \"25.0.0.Final\""
        ).get(0);
        assertNotNull(channel1);

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create()).thenReturn(resolver);
        when(resolver.resolveArtifact(eq("org.foo"), eq("foo"), eq(null), eq(null), anyString())).thenReturn(resolvedArtifactFile);

        // channel order does not matter to determine the latest version
        try (ChannelSession session = new ChannelSession(asList(channel1), factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "foo", null, null, "1.0.0.Final");
            assertNotNull(resolvedArtifact);
            assertEquals("25.0.0.Final", resolvedArtifact.getVersion());
        }
    }

    private static void assertContainsAll(List<MavenArtifact> expected, List<MavenArtifact> actual) {
        List<MavenArtifact> testList = new ArrayList<>(expected);
        for (MavenArtifact a : actual) {
            if (!expected.contains(a)) {
                fail("Unexpected artifact " + a);
            }
            testList.remove(a);
        }
        if (!testList.isEmpty()) {
            fail("Expected artifact not found " + expected.get(0));
        }
    }
}
