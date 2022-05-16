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
package org.wildfly.channel.maven;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

public class VersionResolverFactory implements MavenVersionsResolver.Factory {

    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public VersionResolverFactory(RepositorySystem system,
                                  RepositorySystemSession session,
                                  List<RemoteRepository> repositories) {
        this.system = system;
        this.session = session;
        this.repositories = repositories;
    }

    @Override
    public MavenVersionsResolver create() {
        MavenVersionsResolver res = new MavenResolverImpl(system, session, repositories);
        return res;
    }

    private class MavenResolverImpl implements MavenVersionsResolver {

        private final RepositorySystem system;
        private final RepositorySystemSession session;
        private final List<RemoteRepository> repositories;

        MavenResolverImpl(RepositorySystem system,
                          RepositorySystemSession session,
                          List<RemoteRepository> repositories) {
            this.system = system;
            this.session = session;
            this.repositories = repositories;
        }

        @Override
        public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
            requireNonNull(groupId);
            requireNonNull(artifactId);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, "[0,)");
            VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
            versionRangeRequest.setArtifact(artifact);
            if (repositories != null) {
                versionRangeRequest.setRepositories(repositories);
            }

            try {
                VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
                Set<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toSet());
                return versions;
            } catch (VersionRangeResolutionException e) {
                return emptySet();
            }
        }

        @Override
        public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
            requireNonNull(groupId);
            requireNonNull(artifactId);
            requireNonNull(version);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);

            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            if (repositories != null) {
                request.setRepositories(repositories);
            }

            ArtifactResult result;
            try {
                result = system.resolveArtifact(session, request);
            } catch (ArtifactResolutionException ex) {
                throw new UnresolvedMavenArtifactException(ex.getLocalizedMessage(), ex);
            }
            return result.getArtifact().getFile();
        }
    }

    /**
     * Resolve and read the channels at the specified coordinates.
     *
     * If the {@code ChannelCoordinate} specifies a URL, the channel will be fetched from this URL (without reading the Maven coordinates).
     * If the {@code url} is {@code null}, the channel will be resolved as a Maven coordinate. If the {@code version} is null, the channel artifact will
     * be resolved as the latest version according to the Maven repositories metadata, otherwise the exact artifact for the specified {@code version} will be resolved.
     *
     * @param channelCoords A list of Channel coordinates
     * @return a List of resolved channels
     * @throws UnresolvedMavenArtifactException if the channels can not be resolved
     * @throws MalformedURLException if the channel's rul is not properly formed
     */
    public List<Channel> resolveChannels(List<ChannelCoordinate> channelCoords) throws UnresolvedMavenArtifactException, MalformedURLException {
        requireNonNull(channelCoords);

        List<Channel> channels = new ArrayList<>();
        try (MavenVersionsResolver resolver = create()) {

            for (ChannelCoordinate channelCoord : channelCoords) {
                if (channelCoord.getUrl() != null) {
                    Channel channel = ChannelMapper.from(channelCoord.getUrl());
                    channels.add(channel);
                    continue;
                }

                String version = channelCoord.getVersion();
                if (version == null) {
                    Set<String> versions = resolver.getAllVersions(channelCoord.getGroupId(), channelCoord.getArtifactId(), channelCoord.getExtension(), channelCoord.getClassifier());
                    Optional<String> latestVersion = VersionMatcher.getLatestVersion(versions);
                    version = latestVersion.orElseThrow(() -> {
                        throw new UnresolvedMavenArtifactException(String.format("Unable to resolve the latest version of channel %s:%s", channelCoord.getGroupId(), channelCoord.getArtifactId()));
                    });
                }
                File channelArtifact = resolver.resolveArtifact(channelCoord.getGroupId(), channelCoord.getArtifactId(), channelCoord.getExtension(), channelCoord.getClassifier(), version);
                Channel channel = ChannelMapper.from(channelArtifact.toURI().toURL());
                channels.add(channel);
            }
        }
        return channels;
    }
}


