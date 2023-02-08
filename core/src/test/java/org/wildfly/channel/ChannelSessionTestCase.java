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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static org.wildfly.channel.ChannelManifestMapper.CURRENT_SCHEMA_VERSION;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelSessionTestCase {

    @TempDir
    private Path tempDir;

    @Test
    public void testFindLatestMavenArtifactVersion() throws Exception {
        String manifest =
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(singleton("25.0.0.Final"));

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    public static List<Channel> mockChannel(MavenVersionsResolver resolver, Path tempDir, String... manifests) throws IOException {
        return mockChannel(resolver, tempDir, null, manifests);
    }

    public static List<Channel> mockChannel(MavenVersionsResolver resolver, Path tempDir, Channel.NoStreamStrategy strategy, String... manifests) throws IOException {
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < manifests.length; i++) {
            channels.add(new Channel.Builder()
                            .setManifestCoordinate("org.channels", "channel" + i, "1.0.0")
                            .setResolveStrategy(strategy)
                            .build());
            String manifest = manifests[i];
            Path manifestFile = Files.writeString(tempDir.resolve("manifest" + i +".yaml"), manifest);

            when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("org.channels", "channel" + i, "1.0.0")))))
                    .thenReturn(List.of(manifestFile.toUri().toURL()));
        }
        return channels;
    }

    @Test
    public void testFindLatestMavenArtifactVersionThrowsUnresolvedMavenArtifactException() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(singleton("26.0.0.Final"));

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

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
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(Set.of("25.0.0.Final", "25.0.1.Final"));
        when(resolver.resolveArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.1.Final")).thenReturn(resolvedArtifactFile);

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

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
    public void testResolveLatestMavenArtifactThrowUnresolvedMavenArtifactException() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "schemaVersion: 1.0.0\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: '*'\n" +
                "    versionPattern: '25\\.\\d+\\.\\d+.Final'";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(singleton("26.0.0.Final"));

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

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
    public void testResolveDirectMavenArtifact() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: foo\n" +
                "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveArtifact("org.bar", "bar", null, null, "1.0.0.Final")).thenReturn(resolvedArtifactFile);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.NONE, manifest);

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
    public void testResolveMavenArtifactsFromOneChannel() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.foo\n" +
                                                             "    artifactId: foo\n" +
                                                             "    version: \"25.0.0.Final\"\n" +
                                                             "  - groupId: org.bar\n" +
                                                             "    artifactId: bar\n" +
                                                             "    version: \"26.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "foo", null, null, "1.0.0"),
           new ArtifactCoordinate("org.bar", "bar", null, null, "1.0.0"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
           .thenReturn(asList(resolvedArtifactFile1, resolvedArtifactFile2));

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

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
    public void testResolveMavenArtifactsFromTwoChannel() throws Exception {
        String manifest1 = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.foo\n" +
                                                             "    artifactId: foo\n" +
                                                             "    version: \"25.0.0.Final\"\n";
        String manifest2 =                                   "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.bar\n" +
                                                             "    artifactId: bar\n" +
                                                             "    version: \"26.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
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

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest1, manifest2);

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
    public void testResolveDirectMavenArtifacts() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                                                             "streams:\n" +
                                                             "  - groupId: org.not\n" +
                                                             "    artifactId: used\n" +
                                                             "    version: \"1.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "foo", null, null, "25.0.0.Final"),
           new ArtifactCoordinate("org.bar", "bar", null, null, "26.0.0.Final"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
           .thenReturn(asList(resolvedArtifactFile1, resolvedArtifactFile2));

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

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
    public void testResolveMavenArtifactsFromTwoChannelsWithSameStream() throws Exception {
        String manifest1 = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: foo\n" +
                "    version: \"25.0.0.Final\"";
        String manifest2 = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: foo\n" +
                "    version: \"26.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveArtifact(eq("org.foo"), eq("foo"), eq(null), eq(null), anyString())).thenReturn(resolvedArtifactFile);

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest1, manifest2);

        // channel order does not matter to determine the latest version
        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "foo", null, null, "1.0.0.Final");
            assertNotNull(resolvedArtifact);
            assertEquals("26.0.0.Final", resolvedArtifact.getVersion());
        }

        Collections.reverse(channels);
        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "foo", null, null, "1.0.0.Final");
            assertNotNull(resolvedArtifact);
            assertEquals("26.0.0.Final", resolvedArtifact.getVersion());
        }
    }

    @Test
    public void testChannelWithLatestStrategy() throws Exception {
        String manifest = "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                                                      "streams:\n" +
                                                      "  - groupId: org.foo\n" +
                                                      "    artifactId: foo\n" +
                                                      "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.LATEST, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveArtifact(eq("org.foo"), eq("bar"), eq(null), eq(null), eq("25.0.2.Final"))).thenReturn(resolvedArtifactFile);
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(Set.of("25.0.2.Final", "25.0.1.Final", "25.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final");
            assertNotNull(resolvedArtifact);
            assertEquals("25.0.2.Final", resolvedArtifact.getVersion());
        }
    }

    @Test
    public void testChannelWithLatestStrategyNoArtifact() throws Exception {
        String manifest = "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                          "streams:\n" +
                          "  - groupId: org.foo\n" +
                          "    artifactId: foo\n" +
                          "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.LATEST, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(Set.of());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            Assertions.assertThrows(UnresolvedMavenArtifactException.class, () ->
                session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final")
            );
        }
    }

    @Test
    public void testChannelWithLatestStrategyWithVersionPattern() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                          "streams:\n" +
                          "  - groupId: org.foo\n" +
                          "    artifactId: bar\n" +
                          "    versionPattern: \".*Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.LATEST, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(Set.of("1.0.0"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            Assertions.assertThrows(UnresolvedMavenArtifactException.class, () ->
               session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final")
            );
        }
    }

    @Test
    public void testChannelWithMavenReleaseStrategy() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                          "streams:\n" +
                          "  - groupId: org.foo\n" +
                          "    artifactId: foo\n" +
                          "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.MAVEN_RELEASE, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getMetadataReleaseVersion(eq("org.foo"), eq("bar"))).thenReturn("25.0.1.Final");
        when(resolver.resolveArtifact(eq("org.foo"), eq("bar"), eq(null), eq(null), eq("25.0.1.Final"))).thenReturn(resolvedArtifactFile);
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(Set.of("25.0.1.Final", "25.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final");
            assertEquals("25.0.1.Final", resolvedArtifact.getVersion());
        }
    }

    @Test
    public void testChannelWithMavenLatestStrategy() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                          "streams:\n" +
                          "  - groupId: org.foo\n" +
                          "    artifactId: foo\n" +
                          "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.MAVEN_LATEST, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getMetadataLatestVersion(eq("org.foo"), eq("bar"))).thenReturn("25.0.1.Final");
        when(resolver.resolveArtifact(eq("org.foo"), eq("bar"), eq(null), eq(null), eq("25.0.1.Final"))).thenReturn(resolvedArtifactFile);
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(Set.of("25.0.1.Final", "25.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact resolvedArtifact = session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final");
            assertEquals("25.0.1.Final", resolvedArtifact.getVersion());
        }
    }

    @Test
    public void testChannelWithStrictStrategy() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                          "streams:\n" +
                          "  - groupId: org.foo\n" +
                          "    artifactId: foo\n" +
                          "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        final List<Channel> channels = mockChannel(resolver, tempDir, Channel.NoStreamStrategy.NONE, manifest);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveArtifact(eq("org.foo"), eq("bar"), eq(null), eq(null), eq("25.0.1.Final"))).thenReturn(resolvedArtifactFile);
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(Set.of("25.0.1.Final", "25.0.0.Final"));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            Assertions.assertThrows(UnresolvedMavenArtifactException.class, () ->
               session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final")
            );
        }
    }

    @Test
    public void testChannelWithDefaultStrategy() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                          "streams:\n" +
                          "  - groupId: org.foo\n" +
                          "    artifactId: foo\n" +
                          "    version: \"25.0.0.Final\"";

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile = mock(File.class);

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.resolveArtifact(eq("org.foo"), eq("bar"), eq(null), eq(null), eq("1.0.0.Final"))).thenReturn(resolvedArtifactFile);

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            Assertions.assertThrows(UnresolvedMavenArtifactException.class, () ->
                session.resolveMavenArtifact("org.foo", "bar", null, null, "1.0.0.Final")
            );
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
