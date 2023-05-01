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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.MavenVersionsResolver;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelMapper.CURRENT_SCHEMA_VERSION;

public class ChannelWithBlocklistTestCase {

    @TempDir
    private Path tempDir;
    private static File emptyFile;

    @BeforeAll
    static void setup() throws IOException {
        emptyFile = File.createTempFile("ChannelWithRequirementsTestCase", ".jar");
    }

    @Test
    public void testFindLatestMavenArtifactVersion() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "blocklist:\n" +
                "  maven:\n" +
                "    groupId: org.wildfly\n" +
                "    artifactId: wildfly-blocklist\n" +
                "manifest:\n" +
                "  maven:\n" +
                "    groupId: test\n" +
                "    artifactId: 'test.manifest'\n" +
                "repositories:\n" +
                "  - id: test\n" +
                "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "streams:\n" +
                "  - groupId: org.wildfly\n" +
                "    artifactId: wildfly-ee-galleon-pack\n" +
                "    versionPattern: .*");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionBlocklistDoesntExist() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
           "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
              "blocklist:\n" +
              "  maven:\n" +
              "    groupId: org.wildfly\n" +
              "    artifactId: wildfly-blocklist\n" +
              "manifest:\n" +
              "  maven:\n" +
              "    groupId: test\n" +
              "    artifactId: 'test.manifest'\n" +
              "repositories:\n" +
              "  - id: test\n" +
              "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(Collections.emptyList());

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-blocklist",
                BlocklistCoordinate.EXTENSION, BlocklistCoordinate.CLASSIFIER)).thenReturn(Collections.emptySet());
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.1.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionWithWildcardBlocklist() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "blocklist:\n" +
                        "  maven:\n" +
                        "    groupId: org.wildfly\n" +
                        "    artifactId: wildfly-blocklist\n" +
                        "manifest:\n" +
                        "  maven:\n" +
                        "    groupId: test\n" +
                        "    artifactId: 'test.manifest'\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist-with-wildcards.yaml")));

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionBlocklistsAllVersionsException() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "blocklist:\n" +
                        "  maven:\n" +
                        "    groupId: org.wildfly\n" +
                        "    artifactId: wildfly-blocklist\n" +
                        "manifest:\n" +
                        "  maven:\n" +
                        "    groupId: test\n" +
                        "    artifactId: 'test.manifest'\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(singleton("25.0.1.Final")));

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
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "blocklist:\n" +
                        "  maven:\n" +
                        "    groupId: org.wildfly\n" +
                        "    artifactId: wildfly-blocklist\n" +
                        "manifest:\n" +
                        "  maven:\n" +
                        "    groupId: test\n" +
                        "    artifactId: 'test.manifest'\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));

        File resolvedArtifactFile = emptyFile;

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.0.Final", "25.0.1.Final")));
        when(resolver.resolveArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final")).thenReturn(resolvedArtifactFile);

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            MavenArtifact artifact = session.resolveMavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertNotNull(artifact);

            assertEquals("org.wildfly", artifact.getGroupId());
            assertEquals("wildfly-ee-galleon-pack", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("25.0.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testResolveLatestMavenArtifactThrowUnresolvedMavenArtifactException() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "blocklist:\n" +
                        "  maven:\n" +
                        "    groupId: org.wildfly\n" +
                        "    artifactId: wildfly-blocklist\n" +
                        "manifest:\n" +
                        "  maven:\n" +
                        "    groupId: test\n" +
                        "    artifactId: 'test.manifest'\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.1.Final","26.0.0.Final")));

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
    public void testResolveMavenArtifactsFromOneChannel() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "blocklist:\n" +
                        "  maven:\n" +
                        "    groupId: org.wildfly\n" +
                        "    artifactId: wildfly-blocklist\n" +
                        "manifest:\n" +
                        "  maven:\n" +
                        "    groupId: test\n" +
                        "    artifactId: 'test.manifest'\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: wildfly-ee-galleon-pack\n" +
                        "    versionPattern: \".*\"\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: wildfly-cli\n" +
                        "    version: \"26.0.0.Final\"");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));

        File resolvedArtifactFile1 = emptyFile;
        File resolvedArtifactFile2 = emptyFile;

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null)).thenReturn(new HashSet<>(Set.of("25.0.1.Final","25.0.0.Final")));
        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final"),
           new ArtifactCoordinate("org.wildfly", "wildfly-cli", null, null, "26.0.0.Final"));
        when(resolver.resolveArtifacts(argThat(mavenCoordinates -> mavenCoordinates.size() == 2)))
           .thenReturn(asList(resolvedArtifactFile1, resolvedArtifactFile2));

        try (ChannelSession session = new ChannelSession(channels, factory)) {

            List<MavenArtifact> resolved = session.resolveMavenArtifacts(coordinates);
            assertNotNull(resolved);

            final List<MavenArtifact> expected = asList(
               new MavenArtifact("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final", resolvedArtifactFile1),
               new MavenArtifact("org.wildfly", "wildfly-cli", null, null, "26.0.0.Final", resolvedArtifactFile2)
            );
            assertContainsAll(expected, resolved);

            Optional<Stream> stream = session.getRecordedChannel().findStreamFor("org.wildfly", "wildfly-ee-galleon-pack");
            assertTrue(stream.isPresent());
            assertEquals("25.0.0.Final", stream.get().getVersion());
            stream = session.getRecordedChannel().findStreamFor("org.wildfly", "wildfly-cli");
            assertTrue(stream.isPresent());
            assertEquals("26.0.0.Final", stream.get().getVersion());
        }

        verify(resolver, times(2)).close();
    }

    @Test
    public void testFindLatestMavenArtifactVersionInRequiredChannel() throws Exception {
        List<Channel> channels =List.of(
                new Channel.Builder()
                        .setManifestCoordinate("org.test", "base-manifest", "1.0.0")
                        .addRepository("test", "test")
                        .build(),
                new Channel.Builder()
                        .setManifestCoordinate("org.test", "required-manifest", "1.0.0")
                        .addRepository("test", "test")
                        .setBlocklist("org.wildfly", "wildfly-blocklist", "1.2.3")
                        .build());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("required-manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "requires:\n" +
                        "  - id: required-channel\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: wildfly-ee-galleon-pack\n" +
                        "    versionPattern: \".*\"");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("org.test", "required-manifest", "1.0.0"))), any()))
                .thenReturn(List.of(tempDir.resolve("required-manifest.yaml").toUri().toURL()));

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "id: required-channel\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: wildfly-ee-galleon-pack\n" +
                        "    versionPattern: \".*\"");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("org.test", "base-manifest", "1.0.0"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist", "1.2.3"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/test-blocklist.yaml")));

        when(factory.create(any())).thenReturn(resolver);
        when(resolver.getAllVersions("org.wildfly", "wildfly-ee-galleon-pack", null, null))
           .thenReturn(new HashSet<>(Arrays.asList("25.0.0.Final", "25.0.1.Final")));

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            String version = session.findLatestMavenArtifactVersion("org.wildfly", "wildfly-ee-galleon-pack", null, null, "25.0.0.Final");
            assertEquals("25.0.0.Final", version);
        }

        verify(resolver, times(3)).close();
    }

    @Test
    public void testChannelWithInvalidBlacklist() throws Exception {
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "blocklist:\n" +
                        "  maven:\n" +
                        "    groupId: org.wildfly\n" +
                        "    artifactId: wildfly-blocklist\n" +
                        "manifest:\n" +
                        "  maven:\n" +
                        "    groupId: test\n" +
                        "    artifactId: 'test.manifest'\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: http://test.te");
        assertNotNull(channels);
        assertEquals(1, channels.size());

        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        Files.writeString(tempDir.resolve("manifest.yaml"),
                "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "streams:\n" +
                        "  - groupId: org.wildfly\n" +
                        "    artifactId: '*'\n" +
                        "    versionPattern: '25\\.\\d+\\.\\d+.Final'");
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test", "test.manifest"))), any()))
                .thenReturn(List.of(tempDir.resolve("manifest.yaml").toUri().toURL()));

        when(resolver.resolveChannelMetadata(eq(List.of(new BlocklistCoordinate("org.wildfly", "wildfly-blocklist"))), any()))
                .thenReturn(List.of(this.getClass().getClassLoader().getResource("channels/invalid-blocklist.yaml")));

        when(factory.create(any())).thenReturn(resolver);

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            fail("InvalidChannelException should have been thrown.");
        } catch (InvalidChannelMetadataException e) {
            assertEquals(1, e.getValidationMessages().size());
            assertTrue(e.getValidationMessages().get(0).contains("versions: is missing"), e.getValidationMessages().get(0));
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
