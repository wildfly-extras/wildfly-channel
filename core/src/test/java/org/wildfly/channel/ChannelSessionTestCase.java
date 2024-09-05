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
import static org.wildfly.channel.ChannelManifestMapper.CURRENT_SCHEMA_VERSION;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
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
            VersionResult version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version.getVersion());
        }

        verify(resolver, times(1)).close();
    }

    public static List<Channel> mockChannel(MavenVersionsResolver resolver, Path tempDir, String... manifests) throws IOException {
        return mockChannel(resolver, tempDir, null, manifests);
    }

    public static List<Channel> mockChannel(MavenVersionsResolver resolver, Path tempDir, Channel.NoStreamStrategy strategy, String... manifests) throws IOException {
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < manifests.length; i++) {
            channels.add(new Channel.Builder()
                            .setName("channel-" + i)
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
            } catch (UnresolvedMavenArtifactException ignore) {
                // pass
            }
        }

        verify(resolver, times(1)).close();
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
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));
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
            assertEquals("channel-0", artifact.getChannelName().get());
        }

        verify(resolver, times(1)).close();
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
            } catch (UnresolvedMavenArtifactException ignore) {
                // pass
            }
        }

        verify(resolver, times(1)).close();
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
            assertEquals(Optional.empty(), artifact.getChannelName(), "The channel name should be null when resolving version directly");
        }

        verify(resolver, times(1)).close();
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
        final List<MavenArtifact> expectedArtifacts = asList(
                new MavenArtifact("org.foo", "foo", null, null, "25.0.0.Final", resolvedArtifactFile1, "channel-0"),
                new MavenArtifact("org.bar", "bar", null, null, "26.0.0.Final", resolvedArtifactFile2, "channel-0")
        );

        when(factory.create(any())).thenReturn(resolver);
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "foo", null, null, "1.0.0"),
           new ArtifactCoordinate("org.bar", "bar", null, null, "1.0.0"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
                .thenAnswer(invocationOnMock -> {
                    List<ArtifactCoordinate> coords = invocationOnMock.getArgument(0);
                    return extractFilesInGivenOrder(coords, expectedArtifacts);
                });

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveMavenArtifacts(coordinates);
            assertNotNull(resolved);
            assertContainsAll(expectedArtifacts, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.foo", "foo");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(1)).close();
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
               new MavenArtifact("org.foo", "foo", null, null, "25.0.0.Final", resolvedArtifactFile1, "channel-0"),
               new MavenArtifact("org.bar", "bar", null, null, "26.0.0.Final", resolvedArtifactFile2, "channel-1")
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
               new MavenArtifact("org.foo", "foo", null, null, "25.0.0.Final", resolvedArtifactFile1, "channel-0"),
               new MavenArtifact("org.bar", "bar", null, null, "26.0.0.Final", resolvedArtifactFile2, "channel-0")
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.foo", "foo");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(1)).close();
    }

    @Test
    public void testResolveDirectMavenArtifactsFromTwoChannels() throws Exception {
        String manifest = "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.not\n" +
                "    artifactId: used\n" +
                "    version: \"1.0.0.Final\"";

        /*
         * create two resolvers. The first one will be used by the first channel, the other by the second channel
         * Each resolver is only able to resolve one artifact and throws error when searching for both artifacts
         */
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver1 = mock(MavenVersionsResolver.class);
        MavenVersionsResolver resolver2 = mock(MavenVersionsResolver.class);
        File resolvedArtifactFile1 = mock(File.class);
        File resolvedArtifactFile2 = mock(File.class);

        when(factory.create(any())).thenAnswer(inv->{
            final Channel channel = inv.getArgument(0);
            if (channel.getName().equals("channel-0")) {
                return resolver1;
            } else if (channel.getName().equals("channel-1")) {
                return resolver2;
            } else {
                throw new RuntimeException("Unexpected channel " + channel.getName());
            }
        });

        final ArtifactCoordinate fooArtifact = new ArtifactCoordinate("org.foo", "foo", null, null, "25.0.0.Final");
        final ArtifactCoordinate barArtifact = new ArtifactCoordinate("org.bar", "bar", null, null, "26.0.0.Final");
        final List<ArtifactCoordinate> coordinates = asList(
                fooArtifact,
                barArtifact);
        when(resolver1.resolveArtifacts(any())).thenAnswer(inv -> {
            final List<ArtifactCoordinate> coords = inv.getArgument(0);
            if (coords.size() == 2) {
                throw new ArtifactTransferException("",
                        Set.of(barArtifact),
                        Set.of(new Repository("test", "http://test.te"))
                );
            }  else if (coords.get(0).equals(fooArtifact)) {
                return List.of(resolvedArtifactFile1);
            } else {
                throw new RuntimeException("Unexpected query " + coords);
            }
        });
        when(resolver2.resolveArtifacts(any())).thenAnswer(inv -> {
            final List<ArtifactCoordinate> coords = inv.getArgument(0);
            if (coords.size() == 2) {
                throw new ArtifactTransferException("",
                        Set.of(fooArtifact),
                        Set.of(new Repository("test", "http://test.te"))
                );
            }  else if (coords.get(0).equals(barArtifact)) {
                return List.of(resolvedArtifactFile2);
            } else {
                throw new RuntimeException("Unexpected query " + coords);
            }
        });

        /*
         * create channel session with two channels. The manifests don't matter, but set different names
         */
        final List<Channel> channels = mockChannel(resolver1, tempDir, manifest);
        channels.add(new Channel.Builder(mockChannel(resolver2, tempDir, manifest).get(0))
                .setName("channel-1").build());

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveDirectMavenArtifacts(coordinates);
            assertNotNull(resolved);

            final List<MavenArtifact> expected = asList(
                    new MavenArtifact(fooArtifact.getGroupId(), fooArtifact.getArtifactId(), fooArtifact.getExtension(),
                            fooArtifact.getClassifier(), fooArtifact.getVersion(), resolvedArtifactFile1, "channel-0"),
                    new MavenArtifact(barArtifact.getGroupId(), barArtifact.getArtifactId(), barArtifact.getExtension(),
                            barArtifact.getClassifier(), barArtifact.getVersion(), resolvedArtifactFile2, "channel-1")
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.bar", "bar");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.foo", "foo");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
        }

        verify(resolver1, times(1)).close();
        verify(resolver2, times(1)).close();
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
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(new HashSet<>(Arrays.asList("25.0.2.Final", "25.0.1.Final", "25.0.0.Final")));

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
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(new HashSet<>(Arrays.asList()));

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
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(new HashSet<>(Arrays.asList("1.0.0")));

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
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(new HashSet<>(Arrays.asList("25.0.1.Final", "25.0.0.Final")));

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
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(new HashSet<>(Arrays.asList("25.0.1.Final", "25.0.0.Final")));

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
        when(resolver.getAllVersions("org.foo", "bar", null, null)).thenReturn(new HashSet<>(Arrays.asList("25.0.1.Final", "25.0.0.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            Assertions.assertThrows(UnresolvedMavenArtifactException.class, () ->
               session.resolveMavenArtifact("org.foo", "bar", null, null, "25.0.1.Final")
            );
        }
    }

    @Test
    public void testGetManifests() throws Exception {
        String name1 = "manifest1";
        String name2 = "manifest2";
        Stream[] streams1 = {new Stream("org.foo", "foo", "25.0.0.Final"), new Stream("org.foo", "bar", "23.0.0.Final")};
        Stream[] streams2 = {new Stream("org.bar", "foo", "20.0.0.Final"), new Stream("org.bar", "bar", "21.0.0.Final")};
        String manifest1 = buildManifest(name1, streams1);
        String manifest2 = buildManifest(name2, streams2);

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        when(factory.create(any())).thenReturn(resolver);

        final List<Channel> channels = mockChannel(resolver, tempDir, manifest1, manifest2);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            assertEquals(2,session.getManifests().size());
            checkManifest(session.getManifests().get(0), name1, streams1);
            checkManifest(session.getManifests().get(1), name2, streams2);
        }
    }

    private String buildManifest(String name, Stream... streams) {
        StringBuilder manifest = new StringBuilder("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                          "name: " + name + "\n" +
                          "streams:\n");
        for(Stream stream : streams) {
            manifest.append(
                    "  - groupId: "+ stream.getGroupId() + "\n"
                    + "    artifactId: "+ stream.getArtifactId() + "\n"
                    + "    version: \"" + stream.getVersion() + "\"\n");
        }
        return manifest.toString();
    }
    
    private void checkManifest(ChannelManifest manifest, String name, Stream... streams) {
        assertEquals(name, manifest.getName());
        assertTrue(Set.of(streams).containsAll(manifest.getStreams()));
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

    /**
     * Extracts maven artifact files from the expectedArtifacts argument, and returns them in an order defined by the
     * list in the order argument.
     *
     * @param order ArtifactCoordinate list that defines the order of returned files
     * @param expectedArtifacts MavenArtifact list to extract the artifact files from
     * @return ordered list of files
     */
    static List<File> extractFilesInGivenOrder(List<ArtifactCoordinate> order, List<MavenArtifact> expectedArtifacts) {
        ArrayList<File> files = new ArrayList<>();
        for (ArtifactCoordinate coord: order) {
            Optional<MavenArtifact> first = expectedArtifacts.stream()
                    .filter(a -> a.getArtifactId().equals(coord.getArtifactId()))
                    .findFirst();
            assertTrue(first.isPresent());
            files.add(first.get().getFile());
        }
        return files;
    }
}
