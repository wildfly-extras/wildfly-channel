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
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
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
        versionRangeResult.getRequest().setRepositories(List.of(new RemoteRepository.Builder("test", "default", "file://test").build()));
        final Repository testRepository = new Repository("test", "file://test");
        final ArtifactRepository testArtifactRepository = VersionResolverFactory.DEFAULT_REPOSITORY_MAPPER.apply(testRepository);
        for (Version v : versionRangeResult.getVersions()) versionRangeResult.setRepository(v, testArtifactRepository);
        when(system.resolveVersionRange(eq(session), any(VersionRangeRequest.class))).thenReturn(versionRangeResult);

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel.Builder().addRepository(testRepository.getId(), testRepository.getUrl()).build());

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

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        File resolvedArtifact = resolver.resolveArtifact("org.foo", "bar", null, null, "1.0.0");
        assertEquals(artifactFile, resolvedArtifact);
    }

    @Test
    public void testResolverCanNotResolveArtifact() throws ArtifactResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);
        when(system.resolveArtifact(eq(session), any(ArtifactRequest.class))).thenThrow(ArtifactResolutionException.class);

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

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

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        ChannelCoordinate channelCoord1 = new ChannelCoordinate("org.wildfly", "wildfly-galleon-pack", "27.0.0.Final");
        List<ChannelCoordinate> channelCoords = Arrays.asList(channelCoord1);

        List<Channel> channels = factory.resolveChannels(channelCoords, Collections.emptyList());
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

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        final List<ArtifactCoordinate> coordinates = asList(
           new ArtifactCoordinate("org.foo", "bar", null, null, "1.0.0"),
           new ArtifactCoordinate("org.foo.another", "bar2", null, null, "1.0.0"));
        final List<File> res = resolver.resolveArtifacts(coordinates);

        assertEquals(artifactFile1, res.get(0));
        assertEquals(artifactFile2, res.get(1));
    }

    @Test
    public void testResolverResolveMetadataUsingUrl() throws ArtifactResolutionException, MalformedURLException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        List<URL> resolvedURL = resolver.resolveChannelMetadata(List.of(new ChannelCoordinate(new URL("http://test.channel"))));
        assertEquals(new URL("http://test.channel"), resolvedURL.get(0));
    }

    @Test
    public void testResolverResolveMetadataUsingGa() throws ArtifactResolutionException, MalformedURLException, VersionRangeResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        File artifactFile = new File("test");
        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        Artifact artifact = mock(Artifact.class);
        artifactResult.setArtifact(artifact);
        when (artifact.getFile()).thenReturn(artifactFile);
        VersionRangeResult versionRangeResult = new VersionRangeResult(new VersionRangeRequest());
        Version v100 = mock(Version.class);
        when(v100.toString()).thenReturn("1.0.0");
        Version v110 = mock(Version.class);
        when(v110.toString()).thenReturn("1.1.0");
        Version v111 = mock(Version.class);
        when(v111.toString()).thenReturn("1.1.1");
        versionRangeResult.setVersions(asList(v100, v110, v111));
        versionRangeResult.getRequest().setRepositories(List.of(new RemoteRepository.Builder("test", "default", "file://test").build()));
        final Repository testRepository = new Repository("test", "file://test");
        final ArtifactRepository testArtifactRepository = VersionResolverFactory.DEFAULT_REPOSITORY_MAPPER.apply(testRepository);
        for (Version v : versionRangeResult.getVersions()) versionRangeResult.setRepository(v, testArtifactRepository);
        when(system.resolveVersionRange(eq(session), any())).thenReturn(versionRangeResult);
        final ArgumentCaptor<ArtifactRequest> artifactRequestArgumentCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        when(system.resolveArtifact(eq(session), artifactRequestArgumentCaptor.capture())).thenReturn(artifactResult);

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        List<URL> resolvedURL = resolver.resolveChannelMetadata(List.of(new ChannelCoordinate("org.test", "channel")));
        assertEquals(artifactFile.toURI().toURL(), resolvedURL.get(0));
        assertEquals("1.1.1", artifactRequestArgumentCaptor.getAllValues().get(0).getArtifact().getVersion());
    }

    @Test
    public void testResolverResolveMetadataUsingGav() throws ArtifactResolutionException, MalformedURLException, VersionRangeResolutionException {

        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        File artifactFile = new File("test");
        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        Artifact artifact = mock(Artifact.class);
        artifactResult.setArtifact(artifact);
        when (artifact.getFile()).thenReturn(artifactFile);
        final ArgumentCaptor<ArtifactRequest> artifactRequestArgumentCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        when(system.resolveArtifact(eq(session), artifactRequestArgumentCaptor.capture())).thenReturn(artifactResult);

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        List<URL> resolvedURL = resolver.resolveChannelMetadata(List.of(new ChannelCoordinate("org.test", "channel", "1.0.0")));
        assertEquals(artifactFile.toURI().toURL(), resolvedURL.get(0));
        assertEquals("1.0.0", artifactRequestArgumentCaptor.getAllValues().get(0).getArtifact().getVersion());
    }

    @Test
    public void testRepositoryFactory() throws Exception {
        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        VersionResolverFactory factory = new VersionResolverFactory(system, session, null,
                r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl() + ".new").build());
        MavenVersionsResolver resolver = factory.create(new Channel.Builder()
                        .addRepository("test_1", "http://test_1")
                        .build());

        File artifactFile = new File("test");
        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        Artifact artifact = mock(Artifact.class);
        artifactResult.setArtifact(artifact);
        when(artifact.getFile()).thenReturn(artifactFile);

        ArgumentCaptor<ArtifactRequest> captor = ArgumentCaptor.forClass(ArtifactRequest.class);

        when(system.resolveArtifact(eq(session), captor.capture())).thenReturn(artifactResult);

        resolver.resolveArtifact("group", "artifact", "ext", null, "1.0.0");

        final List<RemoteRepository> actualRepos = captor.getAllValues().get(0).getRepositories();
        assertEquals(1, actualRepos.size());
        assertEquals("http://test_1.new", actualRepos.get(0).getUrl());
    }

    public void testResolveReleaseFromMetadata() throws Exception {
        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        final MetadataResult result1 = getMetadataResult("1.0.0.Final", "1.0.1.Final-SNAPSHOT");
        final MetadataResult result2 = getMetadataResult("1.0.1.Final", "1.0.1.Final-SNAPSHOT");
        when(system.resolveMetadata(eq(session), any())).thenReturn(List.of(result1, result2));

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(any());

        final String res = resolver.getMetadataReleaseVersion("org.foo", "bar");

        assertEquals("1.0.1.Final", res);
    }

    @Test
    public void testResolveLatestFromMetadata() throws Exception {
        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        final MetadataResult result1 = getMetadataResult("1.0.0.Final", "1.0.0.Final");
        final MetadataResult result2 = getMetadataResult("1.0.0.Final", "1.0.1.Final");
        when(system.resolveMetadata(eq(session), any())).thenReturn(List.of(result1, result2));

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        final String res = resolver.getMetadataLatestVersion("org.foo", "bar");

        assertEquals("1.0.1.Final", res);
    }

    @Test
    public void testResolveLatestFromMetadataNoVersioning() throws Exception {
        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        final org.apache.maven.artifact.repository.metadata.Metadata resMetadata = new org.apache.maven.artifact.repository.metadata.Metadata();
        resMetadata.setGroupId("org.foo");
        resMetadata.setArtifactId("bar");

        final MetadataResult result = wrapMetadata(resMetadata);
        when(system.resolveMetadata(eq(session), any())).thenReturn(List.of(result));

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        Assertions.assertThrows(UnresolvedMavenArtifactException.class, () -> {
            resolver.getMetadataLatestVersion("org.foo", "bar");
        });
    }

    @Test
    public void testResolveLatestFromMetadataNoLatestVersion() throws Exception {
        RepositorySystem system = mock(RepositorySystem.class);
        RepositorySystemSession session = mock(RepositorySystemSession.class);

        final MetadataResult result = getMetadataResult("1.0.0.Final", null);
        when(system.resolveMetadata(eq(session), any())).thenReturn(List.of(result));

        VersionResolverFactory factory = new VersionResolverFactory(system, session);
        MavenVersionsResolver resolver = factory.create(new Channel());

        Assertions.assertThrows(UnresolvedMavenArtifactException.class, () -> {
            resolver.getMetadataLatestVersion("org.foo", "bar");
        });
    }

    private MetadataResult getMetadataResult(String releaseVersion, String latestVersion) throws IOException {
        final org.apache.maven.artifact.repository.metadata.Metadata resMetadata = new org.apache.maven.artifact.repository.metadata.Metadata();
        resMetadata.setGroupId("org.foo");
        resMetadata.setArtifactId("bar");
        final Versioning versioning = new Versioning();
        versioning.setRelease(releaseVersion);
        versioning.setLatest(latestVersion);
        versioning.setVersions(List.of("1.0.1.Final-SNAPSHOT", releaseVersion));
        resMetadata.setVersioning(versioning);

        return wrapMetadata(resMetadata);
    }

    private MetadataResult wrapMetadata(org.apache.maven.artifact.repository.metadata.Metadata resMetadata) throws IOException {
        final Path tempFile = Files.createTempFile("test", "xml");
        tempFile.toFile().deleteOnExit();
        new MetadataXpp3Writer().write(new FileWriter(tempFile.toFile()), resMetadata);
        final MetadataResult result = new MetadataResult(new MetadataRequest());
        final Metadata metadata = new DefaultMetadata("org.foo", "bar", "maven-metadata.xml", Metadata.Nature.RELEASE)
           .setFile(tempFile.toFile());
        result.setMetadata(metadata);
        return result;
    }
}

