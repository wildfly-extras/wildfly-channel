/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022, Red Hat, Inc., and individual contributors
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
import org.wildfly.channel.Channel;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.channel.maven.VersionResolverFactory;
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
        ChannelCoordinate channelCoord1 = new ChannelCoordinate();
        channelCoord1.setGroupId("org.wildfly");
        channelCoord1.setArtifactId("wildfly-galleon-pack");
        channelCoord1.setVersion("27.0.0.Final");
        List<ChannelCoordinate> channelCoords = Arrays.asList(channelCoord1);

        List<Channel> channels = factory.resolveChannels(channelCoords);
        assertEquals(1, channels.size());
        Channel channel = channels.get(0);

        assertEquals("My Channel", channel.getName());

    }
}

