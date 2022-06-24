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
package org.wildfly.channel.maven;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class VersionResolverFactoryTest {

    @Test
    public void testResolverGetAllVersions() throws VersionRangeResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        VersionRangeResult versionRangeResult = new VersionRangeResult(new VersionRangeRequest());
        Version v100 = mock(Version.class);
        when(v100.toString()).thenReturn("1.0.0");
        Version v110 = mock(Version.class);
        when(v110.toString()).thenReturn("1.1.0");
        Version v111 = mock(Version.class);
        when(v111.toString()).thenReturn("1.1.1");
        versionRangeResult.setVersions(asList(v100, v110, v111));
        when(system.resolveVersionRange(eq(session), any(VersionRangeRequest.class))).thenReturn(versionRangeResult);

        VersionResolverFactory factory = new VersionResolverFactory(system, session, Collections.emptyList());
        MavenVersionsResolver resolver = factory.create();

        Set<String> allVersions = resolver.getAllVersions("org.foo", "bar", null, null);
        assertEquals(3, allVersions.size());
        assertTrue(allVersions.contains("1.0.0"));
        assertTrue(allVersions.contains("1.1.0"));
        assertTrue(allVersions.contains("1.1.1"));
    }

    @Test
    public void testResolverResolveArtifact() throws ArtifactResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        File artifactFile = mock(File.class);
        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        Artifact artifact = mock(Artifact.class);
        artifactResult.setArtifact(artifact);
        when (artifact.getFile()).thenReturn(artifactFile);
        when(system.resolveArtifact(eq(session), any(ArtifactRequest.class))).thenReturn(artifactResult);

        VersionResolverFactory factory = new VersionResolverFactory(system, session, Collections.emptyList());
        MavenVersionsResolver resolver = factory.create();

        File resolvedArtifact = resolver.resolveArtifact("org.foo", "bar", null, null, "1.0.0");
        assertEquals(artifactFile, resolvedArtifact);
    }

    @Test
    public void testResolverCanNotResolveArtifact() throws ArtifactResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);
        when(system.resolveArtifact(eq(session), any(ArtifactRequest.class))).thenThrow(ArtifactResolutionException.class);

        VersionResolverFactory factory = new VersionResolverFactory(system, session, Collections.emptyList());
        MavenVersionsResolver resolver = factory.create();

        Assertions.assertThrows(UnresolvedMavenArtifactException.class, () -> {
                    resolver.resolveArtifact("org.foo", "does-not-exist", null, null, "1.0.0");
                });

    }

    @Test
    public void testFactoryCanNotResolveChannel() throws MalformedURLException, ArtifactResolutionException, URISyntaxException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL url = tccl.getResource("channels/channel.yaml");
        Artifact channelArtifact = new DefaultArtifact("org.wildfly:wildfly-galleon-pack:yaml:channel:27.0.0.Final");
        channelArtifact = channelArtifact.setFile(new File(url.toURI()));

        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        artifactResult.setArtifact(channelArtifact);
        when(system.resolveArtifact(eq(session), any(ArtifactRequest.class))).thenReturn(artifactResult);

        VersionResolverFactory factory = new VersionResolverFactory(system, session, Collections.emptyList());
        ChannelCoordinate channelCoord1 = new ChannelCoordinate("org.wildfly", "wildfly-galleon-pack", "27.0.0.Final");
        List<ChannelCoordinate> channelCoords = Arrays.asList(channelCoord1);

        List<Channel> channels = factory.resolveChannels(channelCoords);
        assertEquals(1, channels.size());
        Channel channel = channels.get(0);

        assertEquals("My Channel", channel.getName());

    }

    @Test
    public void testResolverResolveAllArtifacts() throws ArtifactResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        File artifactFile1 = mock(File.class);
        ArtifactResult artifactResult1 = new ArtifactResult(new ArtifactRequest());
        Artifact artifact1 = new DefaultArtifact("org.foo", "bar", null, null, "1.0.0", null, artifactFile1);
        artifactResult1.setArtifact(artifact1);

        File artifactFile2 = mock(File.class);
        ArtifactResult artifactResult2 = new ArtifactResult(new ArtifactRequest());
        Artifact artifact2 = new DefaultArtifact("org.foo.another", "bar2", null, null, "1.0.0", null, artifactFile2);
        artifactResult2.setArtifact(artifact2);

        when(system.resolveArtifacts(eq(session), any(List.class))).thenReturn(Arrays.asList(artifactResult1, artifactResult2));

        VersionResolverFactory factory = new VersionResolverFactory(system, session, Collections.emptyList());
        MavenVersionsResolver resolver = factory.create();

        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "bar", null, null, "1.0.0"),
           new ArtifactCoordinate("org.foo.another", "bar2", null, null, "1.0.0"));
        final List<File> res = resolver.resolveArtifacts(coordinates);

        assertEquals(artifactFile1, res.get(0));
        assertEquals(artifactFile2, res.get(1));
    }
}

