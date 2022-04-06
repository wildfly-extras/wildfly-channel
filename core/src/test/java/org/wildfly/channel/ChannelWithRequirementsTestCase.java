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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        // create a Mock MavenVersionsResolver that will be used by the required channel
        MavenVersionsResolver requiredChannelResolver = mock(MavenVersionsResolver.class);


        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        when(factory.create())
                .thenReturn(resolver, requiredChannelResolver);
        when(resolver.resolveLatestVersionFromMavenMetadata("org.foo", "required-channel", "yaml", "channel"))
                .thenReturn(resolvedRequiredChannelFile);
        when(requiredChannelResolver.getAllVersions("org.example", "foo-bar", null, null))
                .thenReturn(Set.of("1.0.0.Final, 1.1.0.Final", "1.2.0.Final"));
        when(requiredChannelResolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);

        List<Channel> channels = ChannelMapper.fromString(
                "name: My Channel\n" +
                        "requires:\n" +
                        "  - groupId: org.foo\n" +
                        "    artifactId: required-channel");
        assertEquals(1, channels.size());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveLatestMavenArtifact("org.example", "foo-bar", null, null);
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
        // create a Mock MavenVersionsResolver that will resolve the required channel
        MavenVersionsResolver resolver = mock(MavenVersionsResolver.class);
        // create a Mock MavenVersionsResolver that will be used by the required channel
        MavenVersionsResolver requiredChannelResolver = mock(MavenVersionsResolver.class);


        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL resolvedRequiredChannelURL = tccl.getResource("channels/required-channel.yaml");
        File resolvedRequiredChannelFile = Paths.get(resolvedRequiredChannelURL.toURI()).toFile();
        File resolvedArtifactFile = mock(File.class);

        when(factory.create())
                .thenReturn(resolver, requiredChannelResolver);
        when(resolver.resolveArtifact("org.foo", "required-channel", "yaml", "channel", "2.0.0.Final"))
                .thenReturn(resolvedRequiredChannelFile);
        when(requiredChannelResolver.resolveArtifact("org.example", "foo-bar", null, null, "1.2.0.Final"))
                .thenReturn(resolvedArtifactFile);

        List<Channel> channels = ChannelMapper.fromString(
                "name: My Channel\n" +
                        "requires:\n" +
                        "  - groupId: org.foo\n" +
                        "    artifactId: required-channel\n" +
                        "    version: 2.0.0.Final");
        assertEquals(1, channels.size());

        try (ChannelSession session = new ChannelSession(channels, factory)) {
            MavenArtifact artifact = session.resolveLatestMavenArtifact("org.example", "foo-bar", null, null);
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
