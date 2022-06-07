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
}
