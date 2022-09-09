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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelWithRequirementsTestCase {

    @TempDir
    private Path tempDir;

    /**
     * Test that newest version of required channel is used when required channel version is not specified
     */
    @Test
    public void testChannelWhichRequiresAnotherChannel() throws UnresolvedMavenArtifactException, URISyntaxException, MalformedURLException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        // create a Mock MavenVersionsResolver that will resolve the required channel
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        URL resolvedRequiredManifestURL = tccl.getResource("channels/required-manifest.yaml");

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.getAllVersions("org.foo", "required-channel", "yaml", "channel"))
                .thenReturn(Set.of("1", "2", "3"));
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "3"))
                .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);
        when(resolver.getAllVersions("org.example", "foo-bar", null, null))
                .thenReturn(Set.of("1.0.0.Final, 1.1.0.Final", "1.2.0.Final"));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);
        when(resolver.resolveChannelMetadata(any())).thenReturn(List.of(resolvedRequiredManifestURL));

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "repositories:\n" +
                "  - id: test\n" +
                "    url: test");
        assertEquals(1, channels.size());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.2.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }
    }

    /**
     * Test that specific version of required channel is used when required
     */
    @Test
    public void testChannelWhichRequiresAnotherVersionedChannel() throws UnresolvedMavenArtifactException, URISyntaxException, MalformedURLException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);


        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        URL resolvedRequiredManifestURL = tccl.getResource("channels/required-manifest.yaml");

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);
        when(resolver.resolveChannelMetadata(any())).thenReturn(List.of(resolvedRequiredManifestURL));

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "repositories:\n" +
                "  - id: test\n" +
                "    url: test");
        assertEquals(1, channels.size());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.2.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile, artifact.getFile());
        }
    }


    /**
     * Test that stream from requiring channel overrides stream from required channel
     *
     * Given requiring channel specifies stream version different from version specified in required channel.
     * When channel is resolved
     * Then requiring channel version then MUST be used in resolution.
     */
    @Test
    public void testRequiringChannelOverridesStreamFromRequiredChannel() throws UnresolvedMavenArtifactException, URISyntaxException, IOException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();

        URL resolvedRequiredManifestURL = tccl.getResource("channels/required-manifest.yaml");

        File resolvedArtifactFile120Final = mock(File.class);
        File resolvedArtifactFile200Final = mock(File.class);
        File resolvedArtifactFile100Final = mock(File.class);

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        // There are 2 version of foo-bar
        when(resolver.getAllVersions("org.example", "foo-bar", null, null))
                .thenReturn(Set.of("1.0.0.Final", "1.2.0.Final", "2.0.0.Final"));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.0.0.Final"))
                .thenReturn(resolvedArtifactFile100Final);
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile120Final);
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "2.0.0.Final"))
                .thenReturn(resolvedArtifactFile200Final);
        when(resolver.resolveChannelMetadata(eq(List.of(new ChannelManifestCoordinate("test.channels", "required-manifest", "1.0.0")))))
                .thenReturn(List.of(resolvedRequiredManifestURL));

        // The requiring channel requires newer version of foo-bar artifact
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "manifest:\n" +
                "  maven:\n" +
                "    groupId: org.channels\n" +
                "    artifactId: base-manifest\n" +
                "    version: 1.0.0\n" +
                "repositories:\n" +
                "- id: test\n" +
                "  url: test-repository");

        String manifest = "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: My manifest\n" +
                "streams:\n" +
                "  - groupId: org.example\n" +
                "    artifactId: foo-bar\n" +
                "    version: 2.0.0.Final";

        mockManifest(resolver, manifest, "org.channels:base-manifest:1.0.0");

        assertEquals(1, channels.size());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile200Final, artifact.getFile());
        }


        // The requiring channel requires older version of foo-bar artifact.
        manifest = "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "name: My Channel\n" +
                        "streams:\n" +
                        "  - groupId: org.example\n" +
                        "    artifactId: foo-bar\n" +
                        "    version: 1.0.0.Final";

        mockManifest(resolver, manifest, "org.channels:base-manifest:1.0.0");

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.0.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile100Final, artifact.getFile());
        }


        // The requiring channel specifies wildcard for version, newest version should be used
        // the newest version is 2.0.0.Final
        manifest = "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "name: My Channel\n" +
                        "streams:\n" +
                        "  - groupId: org.example\n" +
                        "    artifactId: foo-bar\n" +
                        "    versionPattern: '.*'";
        mockManifest(resolver, manifest, "org.channels:base-manifest:1.0.0");

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());
            assertEquals(resolvedArtifactFile200Final, artifact.getFile());
        }
    }

    /**
     * Test that nested requiring channels stream inheritance
     */
    @Test
    public void testChannelRequirementNesting() throws UnresolvedMavenArtifactException, URISyntaxException, IOException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        URL resolved2ndLevelRequiringChannelURL = tccl.getResource("channels/2nd-level-requiring-channel.yaml");
        File resolved2ndLevelRequiringChannelFile = Paths.get(resolved2ndLevelRequiringChannelURL.toURI()).toFile();

        URL resolvedRequiredManifestURL = tccl.getResource("channels/required-manifest.yaml");

        URL resolvedRequiredManifestURL2nd = tccl.getResource("channels/2nd-level-requiring-manifest.yaml");

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.foo", "2nd-level-requiring-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolved2ndLevelRequiringChannelFile);
        mockManifest(resolver, resolvedRequiredManifestURL, "test.channels:required-manifest:1.0.0");
        mockManifest(resolver, resolvedRequiredManifestURL2nd, "test.channels:required-2nd-level-manifest:1.0.0");
        // There are:
        // 3 version of foo-bar
        // 2 versions of im-only-in-required-channel
        // 2 versions of im-only-in-second-level
        when(resolver.getAllVersions("org.example", "foo-bar", null, null))
                .thenReturn(Set.of("1.0.0.Final", "1.2.0.Final", "2.0.0.Final"));
        when(resolver.getAllVersions("org.example", "im-only-in-required-channel", null, null))
                .thenReturn(Set.of("1.0.0.Final", "2.0.0.Final"));
        when(resolver.getAllVersions("org.example", "im-only-in-second-level", null, null))
                .thenReturn(Set.of("1.0.0.Final", "2.0.0.Final"));

        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "2.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "im-only-in-required-channel", null, null, "1.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "im-only-in-required-channel", null, null, "2.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "im-only-in-second-level", null, null, "1.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "im-only-in-second-level", null, null, "2.0.0.Final"))
                .thenReturn(mock(File.class));

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                        "name: root level requiring channel\n"+
                        "requires:\n" +
                        "  - groupId: org.foo\n" +
                        "    artifactId: 2nd-level-requiring-channel\n" +
                        "    version: 2.0.0.Final\n" +
                        "repositories:\n" +
                        "  - id: test\n" +
                        "    url: test");

        // check that streams from required channel propagate to root channel
        try (ChannelSession session = new ChannelSession(channels, factory)) {
            // foo-bar should get version from layer 2

            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.0.0.Final", artifact.getVersion());

            // im-only-in-required-channel should propagate to the root level channel
            artifact = session.resolveMavenArtifact("org.example", "im-only-in-required-channel", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("im-only-in-required-channel", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.0.0.Final", artifact.getVersion());

            // im-only-in-second-level should propagate to the root level channel
            artifact = session.resolveMavenArtifact("org.example", "im-only-in-second-level", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("im-only-in-second-level", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.0.0.Final", artifact.getVersion());
        }

        // check that root level can override all streams from required channels
        channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: 2nd-level-requiring-channel\n" +
                "    version: 2.0.0.Final\n" +
                "manifest:\n" +
                "  maven:\n" +
                "    groupId: org.channels\n" +
                "    artifactId: base-manifest\n" +
                "    version: 1.0.0\n" +
                "repositories:\n" +
                "  - id: test\n" +
                "    url: test");
        String manifest = "schemaVersion: " + ChannelManifestMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring manifest\n" +
                "streams:\n" +
                "  - groupId: org.example\n" +
                "    artifactId: im-only-in-required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.example\n" +
                "    artifactId: foo-bar\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.example\n" +
                "    artifactId: im-only-in-second-level\n" +
                "    version: 2.0.0.Final";

        mockManifest(resolver, manifest, "org.channels:base-manifest:1.0.0");

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            // foo-bar should get version from layer 2

            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());

            // im-only-in-required-channel should propagate to the root level channel
            artifact = session.resolveMavenArtifact("org.example", "im-only-in-required-channel", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("im-only-in-required-channel", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());

            // im-only-in-second-level should propagate to the root level channel
            artifact = session.resolveMavenArtifact("org.example", "im-only-in-second-level", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("im-only-in-second-level", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());
        }
    }

    /**
     * Test that multiple requirements are propagating artifacts versions correctly
     * If multiple required channels define the same stream, newest defined version of the stream will be used
     */
    @Test
    public void testChannelMultipleRequirements() throws UnresolvedMavenArtifactException, URISyntaxException, IOException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        URL resolvedRequiredChannel2URL = tccl.getResource("channels/required-channel-2.yaml");
        File resolvedRequiredChannel2File = Paths.get(resolvedRequiredChannel2URL.toURI()).toFile();

        URL resolvedRequiredManifestURL = tccl.getResource("channels/required-manifest.yaml");

        URL resolvedRequiredManifestURL2 = tccl.getResource("channels/required-manifest-2.yaml");

        when(factory.create(any()))
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.foo", "required-channel-2", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannel2File);

        when(resolver.getAllVersions("org.example", "foo-bar", null, null))
                .thenReturn(Set.of("1.0.0.Final", "1.2.0.Final", "2.0.0.Final"));
        when(resolver.getAllVersions("org.example", "im-only-in-required-channel", null, null))
                .thenReturn(Set.of("1.0.0.Final", "2.0.0.Final"));

        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "2.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "im-only-in-required-channel", null, null, "1.0.0.Final"))
                .thenReturn(mock(File.class));
        when(resolver.resolveArtifact("org.example", "im-only-in-required-channel", null, null, "2.0.0.Final"))
                .thenReturn(mock(File.class));

        mockManifest(resolver, resolvedRequiredManifestURL, "test.channels:required-manifest:1.0.0");
        mockManifest(resolver, resolvedRequiredManifestURL2, "test.channels:required-manifest-2:1.0.0");

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel-2\n" +
                "    version: 2.0.0.Final\n" +
                "repositories:\n" +
                "  - id: test\n" +
                "    url: test");

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());

            // im-only-in-required-channel should propagate to the root level channel
            artifact = session.resolveMavenArtifact("org.example", "im-only-in-required-channel", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("im-only-in-required-channel", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.0.0.Final", artifact.getVersion());
        }

        channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel-2\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "repositories:\n" +
                "  - id: test\n" +
                "    url: test");

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveMavenArtifact("org.example", "foo-bar", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("foo-bar", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("2.0.0.Final", artifact.getVersion());

            // im-only-in-required-channel should propagate to the root level channel
            artifact = session.resolveMavenArtifact("org.example", "im-only-in-required-channel", null, null, "0");
            assertNotNull(artifact);

            assertEquals("org.example", artifact.getGroupId());
            assertEquals("im-only-in-required-channel", artifact.getArtifactId());
            assertNull(artifact.getExtension());
            assertNull(artifact.getClassifier());
            assertEquals("1.0.0.Final", artifact.getVersion());
        }
    }

    private void mockManifest(MavenVersionsResolver resolver, String manifest, String gav) throws IOException {
        Path manifestFile = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestFile, manifest);

        mockManifest(resolver, manifestFile.toUri().toURL(), gav);
    }

    @Test
    public void testRequiredChannelIgnoresNoStreamStrategy() throws UnresolvedMavenArtifactException, URISyntaxException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        // create a Mock MavenVersionsResolver that will resolve the required channel
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        // the default strategy is ORIGINAL
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        URL resolvedRequiredManifestURL = tccl.getResource("channels/required-manifest.yaml");
        when(resolver.resolveChannelMetadata(any())).thenReturn(List.of(resolvedRequiredManifestURL));

        when(factory.create(any()))
           .thenReturn(resolver);
        when(resolver.getAllVersions("org.foo", "required-channel", "yaml", "channel"))
           .thenReturn(Set.of("1", "2", "3"));
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "3"))
           .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "1.2.0.Final"))
           .thenReturn(resolvedArtifactFile);
        when(resolver.getAllVersions("org.example", "foo-bar", null, null))
           .thenReturn(Set.of("1.0.0.Final, 1.1.0.Final", "1.2.0.Final"));
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
           .thenReturn(resolvedArtifactFile);

        // strict NoStreamStrategy should result in no matches
        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + ChannelMapper.CURRENT_SCHEMA_VERSION + "\n" +
                                                             "resolves-if-no-stream: none\n" +
                                                             "name: My Channel\n" +
                                                             "requires:\n" +
                                                             "  - groupId: org.foo\n" +
                                                             "    artifactId: required-channel\n" +
                                                             "repositories:\n" +
                                                             "  - id: test\n" +
                                                             "    url: http://test.te");
        assertEquals(1, channels.size());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            Assertions.assertThrows(UnresolvedMavenArtifactException.class, ()->
                session.resolveMavenArtifact("org.example", "idontexist", null, null, "1.2.3")
            );
        }
    }

    private void mockManifest(MavenVersionsResolver resolver, URL manifestUrl, String gavString) throws IOException {
        final String[] splitGav = gavString.split(":");
        final MavenCoordinate gav = new MavenCoordinate(splitGav[0], splitGav[1], splitGav.length == 3 ? splitGav[2] : null);
        when(resolver.resolveChannelMetadata(eq(List.of(ChannelManifestCoordinate.create(null, gav)))))
                .thenReturn(List.of(manifestUrl));
    }
}
