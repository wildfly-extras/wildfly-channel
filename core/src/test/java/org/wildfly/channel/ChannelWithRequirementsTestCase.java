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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.wildfly.channel.ChannelMapper.CURRENT_SCHEMA_VERSION;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelWithRequirementsTestCase {

    /**
     * Test that newest version of required channel is used when required channel version is not specified
     */
    @Test
    public void testChannelWhichRequiresAnotherChannel() throws UnresolvedMavenArtifactException, URISyntaxException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        // create a Mock MavenVersionsResolver that will resolve the required channel
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        when(factory.create())
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

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel");
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
    public void testChannelWhichRequiresAnotherVersionedChannel() throws UnresolvedMavenArtifactException, URISyntaxException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);


        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        when(factory.create())
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final");
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
    public void testRequiringChannelOverridesStreamFromRequiredChannel() throws UnresolvedMavenArtifactException, URISyntaxException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();

        File resolvedArtifactFile120Final = mock(File.class);
        File resolvedArtifactFile200Final = mock(File.class);
        File resolvedArtifactFile100Final = mock(File.class);

        when(factory.create())
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

        // The requiring channel requires newer version of foo-bar artifact
        List<Channel> channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: My Channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "streams:\n" +
                "  - groupId: org.example\n" +
                "    artifactId: foo-bar\n" +
                "    version: 2.0.0.Final");

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
        channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "name: My Channel\n" +
                        "requires:\n" +
                        "  - groupId: org.foo\n" +
                        "    artifactId: required-channel\n" +
                        "    version: 2.0.0.Final\n" +
                        "streams:\n" +
                        "  - groupId: org.example\n" +
                        "    artifactId: foo-bar\n" +
                        "    version: 1.0.0.Final");

        assertEquals(1, channels.size());

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
        channels = ChannelMapper.fromString(
                "schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "name: My Channel\n" +
                        "requires:\n" +
                        "  - groupId: org.foo\n" +
                        "    artifactId: required-channel\n" +
                        "    version: 2.0.0.Final\n" +
                        "streams:\n" +
                        "  - groupId: org.example\n" +
                        "    artifactId: foo-bar\n" +
                        "    versionPattern: '.*'");

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
    }

    /**
     * Test that nested requiring channels stream inheritance
     */
    @Test
    public void testChannelRequirementNesting() throws UnresolvedMavenArtifactException, URISyntaxException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        URL resolved2ndLevelRequiringChannelURL = tccl.getResource("channels/2nd-level-requiring-channel.yaml");
        File resolved2ndLevelRequiringChannelFile = Paths.get(resolved2ndLevelRequiringChannelURL.toURI()).toFile();

        when(factory.create())
                .thenReturn(resolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        when(resolver.resolveArtifact("org.foo", "2nd-level-requiring-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolved2ndLevelRequiringChannelFile);
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

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                        "name: root level requiring channel\n"+
                        "requires:\n" +
                        "  - groupId: org.foo\n" +
                        "    artifactId: 2nd-level-requiring-channel\n" +
                        "    version: 2.0.0.Final");

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
        channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: 2nd-level-requiring-channel\n" +
                "    version: 2.0.0.Final\n" +
                "streams:\n" +
                "  - groupId: org.example\n" +
                "    artifactId: im-only-in-required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.example\n" +
                "    artifactId: foo-bar\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.example\n" +
                "    artifactId: im-only-in-second-level\n" +
                "    version: 2.0.0.Final");

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
    public void testChannelMultipleRequirements() throws UnresolvedMavenArtifactException, URISyntaxException {
        MavenVersionsResolver.Factory factory = mock(MavenVersionsResolver.Factory.class);
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        URL resolvedRequiredChannel2URL = tccl.getResource("channels/required-channel-2.yaml");
        File resolvedRequiredChannel2File = Paths.get(resolvedRequiredChannel2URL.toURI()).toFile();

        when(factory.create())
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

        List<Channel> channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel-2\n" +
                "    version: 2.0.0.Final");

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

        channels = ChannelMapper.fromString("schemaVersion: " + CURRENT_SCHEMA_VERSION + "\n" +
                "name: root level requiring channel\n" +
                "requires:\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel-2\n" +
                "    version: 2.0.0.Final\n" +
                "  - groupId: org.foo\n" +
                "    artifactId: required-channel\n" +
                "    version: 2.0.0.Final"
        );

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
}