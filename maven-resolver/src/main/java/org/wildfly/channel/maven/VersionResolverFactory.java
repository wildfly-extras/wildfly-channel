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

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.NoStreamFoundException;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;
import org.jboss.logging.Logger;

public class VersionResolverFactory implements MavenVersionsResolver.Factory {

    private static final Logger LOG = Logger.getLogger(VersionResolverFactory.class);

    /**
     * The way checksum verification should be handled. It can be "fail", "warn", "ignore" or null
     */
    private static final String checksumPolicy = System.getProperty("org.wildfly.channel.maven.policy.checksum", RepositoryPolicy.CHECKSUM_POLICY_FAIL);
    /**
     * maximum number of retries to perform on each artifact
     */
    private static final int MAX_RESOLVER_RETRIES = Integer.getInteger("org.wildfly.channel.maven.resolve.retry.max", 5);
    /**
     * delay between retry attempts in milliseconds
     */
    private static final long TIMEOUT_RESOLVER_RETRIES = Long.getLong("org.wildfly.channel.maven.resolve.retry.timeout", 500);
    public static final RepositoryPolicy DEFAULT_REPOSITORY_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, checksumPolicy);
    public static final Function<Repository, RemoteRepository> DEFAULT_REPOSITORY_MAPPER = r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl())
            .setPolicy(DEFAULT_REPOSITORY_POLICY)
            .build();

    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final Function<Repository, RemoteRepository> repositoryFactory;

    public VersionResolverFactory(RepositorySystem system,
                                  RepositorySystemSession session) {
        this(system, session, DEFAULT_REPOSITORY_MAPPER);
    }
    public VersionResolverFactory(RepositorySystem system,
                                  RepositorySystemSession session,
                                  Function<Repository, RemoteRepository> repositoryFactory) {
        this.system = system;
        this.session = session;
        this.repositoryFactory = repositoryFactory;
    }

    @Override
    public MavenVersionsResolver create(Collection<Repository> repositories) {
        Objects.requireNonNull(repositories);

        final List<RemoteRepository> mvnRepositories = repositories.stream()
                .map(repositoryFactory::apply)
                .collect(Collectors.toList());
        return create(mvnRepositories);
    }

    private MavenResolverImpl create(List<RemoteRepository> mvnRepositories) {
        return new MavenResolverImpl(system, session, mvnRepositories);
    }

    private class MavenResolverImpl implements MavenVersionsResolver {

        private final RepositorySystem system;
        private final RepositorySystemSession session;
        private final List<RemoteRepository> repositories;
        private final RetryHandler retryingResolver;

        MavenResolverImpl(RepositorySystem system,
                          RepositorySystemSession session,
                          List<RemoteRepository> repositories) {
            this.system = system;
            this.session = session;
            this.repositories = repositories;
            this.retryingResolver = new RetryHandler(MAX_RESOLVER_RETRIES, TIMEOUT_RESOLVER_RETRIES, TimeUnit.MILLISECONDS);
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

            VersionRangeResult versionRangeResult = retryingResolver.attemptResolveMetadata(() -> {
                try {
                    return system.resolveVersionRange(session, versionRangeRequest);
                } catch (VersionRangeResolutionException e) {
                    // thrown only if unable to parse version range
                    // we use hardcoded version range, so this should not happen
                    throw new RuntimeException(e);
                }
            }, attemptedRepositories());

            final RemoteArtifactVersionsFilter remoteOnlyFilter = new RemoteArtifactVersionsFilter(session, versionRangeResult);
            final List<Version> remoteVersions = versionRangeResult.getVersions().stream()
                    .filter(remoteOnlyFilter::accept)
                    .collect(Collectors.toList());

            if (!versionRangeResult.getVersions().isEmpty() && remoteVersions.isEmpty()) {
                LOG.warnf("Error resolving artifact %s:%s versions: the only available version was resolved from local repository, discarding!",
                        artifact.getGroupId(), artifact.getArtifactId());
            }

            return remoteVersions.stream()
                    .map(Version::toString)
                    .collect(Collectors.toSet());

        }

        @Override
        public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws ArtifactTransferException {
            requireNonNull(groupId);
            requireNonNull(artifactId);
            requireNonNull(version);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);

            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            if (repositories != null) {
                request.setRepositories(repositories);
            }

            return retryingResolver.attemptResolve(()->{
                        final ArtifactResult artifactResult = system.resolveArtifact(session, request);
                        if (LOG.isDebugEnabled()) {
                            for (Exception exception : artifactResult.getExceptions()) {
                                LOG.debug(String.format("Error resolving maven artifact %s: %s", artifactResult.getRequest().getArtifact(),exception.getMessage()), exception);
                            }
                        }
                        return List.of(artifactResult.getArtifact().getFile());
            },
                    (ex)->singleton(new ArtifactCoordinate(groupId, artifactId, extension, classifier, version)),
                    attemptedRepositories()).get(0);
        }

        @Override
        public List<File> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
            requireNonNull(coordinates);

            List<ArtifactRequest> requests = new ArrayList<>();
            for (ArtifactCoordinate coord : coordinates) {
                Artifact artifact = new DefaultArtifact(coord.getGroupId(), coord.getArtifactId(), coord.getClassifier(), coord.getExtension(), coord.getVersion());

                ArtifactRequest request = new ArtifactRequest();
                request.setArtifact(artifact);
                if (repositories != null) {
                    request.setRepositories(repositories);
                }
                requests.add(request);
            }

            final RetryHandler.Supplier artifactQuery = () -> {
                final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);
                for (ArtifactResult artifactResult : artifactResults) {
                    if (LOG.isDebugEnabled()) {
                        for (Exception exception : artifactResult.getExceptions()) {
                            LOG.debug(String.format("Error resolving maven artifact %s: %s", artifactResult.getRequest().getArtifact(), exception.getMessage()), exception);
                        }
                    }
                }
                // results are in the same order as requests
                return artifactResults.stream()
                        .map(ArtifactResult::getArtifact)
                        .map(Artifact::getFile)
                        .collect(Collectors.toList());
            };
            final Function<ArtifactResolutionException, Set<ArtifactCoordinate>> exceptionMaper = (ex) -> ex.getResults().stream()
                    .filter(r -> r.getArtifact() == null)
                    .map(res -> res.getRequest().getArtifact())
                    .map(a -> new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), a.getVersion()))
                    .collect(Collectors.toSet());

            return retryingResolver.attemptResolve(artifactQuery,
                    exceptionMaper,
                    attemptedRepositories());
        }

        @Override
        public List<URL> resolveChannelMetadata(List<? extends ChannelMetadataCoordinate> coords) throws ArtifactTransferException {
            requireNonNull(coords);

            List<URL> channels = new ArrayList<>();

            for (ChannelMetadataCoordinate coord : coords) {
                if (coord.getUrl() != null) {
                    LOG.infof("Resolving channel metadata at %s", coord.getUrl());
                    channels.add(coord.getUrl());
                    continue;
                }

                String version = coord.getVersion();
                if (version == null) {
                    Set<String> versions = getAllVersions(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier());
                    Optional<String> latestVersion = VersionMatcher.getLatestVersion(versions);
                    version = latestVersion.orElseThrow(() ->
                        new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata %s:%s", coord.getGroupId(), coord.getArtifactId()),
                                singleton(new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), "")),
                                attemptedRepositories()));
                }
                LOG.infof("Resolving channel metadata from Maven artifact %s:%s:%s", coord.getGroupId(), coord.getArtifactId(), version);
                File channelArtifact = resolveArtifact(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), version);
                try {
                    channels.add(channelArtifact.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new ArtifactTransferException(String.format("Unable to resolve the latest version of channel metadata %s:%s", coord.getGroupId(), coord.getArtifactId()), e,
                            singleton(new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(),
                                    coord.getExtension(), coord.getClassifier(), coord.getVersion())),
                            attemptedRepositories());
                }
            }
            return channels;
        }

        @Override
        public String getMetadataReleaseVersion(String groupId, String artifactId) {
            requireNonNull(groupId);
            requireNonNull(artifactId);

            final List<MetadataResult> metadataResults = getMavenMetadata(groupId, artifactId);

            final Function<org.apache.maven.artifact.repository.metadata.Metadata, String> getVersion = m -> m.getVersioning().getRelease();
            return findLatestMetadataVersion(metadataResults, getVersion, groupId, artifactId);
        }

        @Override
        public String getMetadataLatestVersion(String groupId, String artifactId) {
            requireNonNull(groupId);
            requireNonNull(artifactId);

            final List<MetadataResult> metadataResults = getMavenMetadata(groupId, artifactId);

            return findLatestMetadataVersion(metadataResults, m -> m.getVersioning().getLatest(), groupId, artifactId);
        }

        private String findLatestMetadataVersion(List<MetadataResult> metadataResults,
                                                 Function<org.apache.maven.artifact.repository.metadata.Metadata, String> getVersion,
                                                 String groupId, String artifactId) {
            final MetadataXpp3Reader reader = new MetadataXpp3Reader();
            return metadataResults.stream()
                    .filter(r -> r.getMetadata() != null)
                    .map(m -> m.getMetadata().getFile())
                    .map(f -> {
                        try {
                            return reader.read(new FileReader(f));
                        } catch (IOException | XmlPullParserException e) {
                            final ArtifactCoordinate requestedArtifact = new ArtifactCoordinate(groupId, artifactId, null, null, "*");
                            throw new ArtifactTransferException(e.getLocalizedMessage(), e, singleton(requestedArtifact),
                                    attemptedRepositories());
                        }
                    })
                    .filter(m->m.getVersioning() != null)
                    .map(getVersion)
                    .filter(s->s!=null&&!s.isEmpty())
                    .max(COMPARATOR)
                    .orElseThrow(()-> new NoStreamFoundException("No versioning information found in metadata.",
                        singleton(new ArtifactCoordinate(groupId, artifactId, null, null, "*")),
                            attemptedRepositories()));
        }

        private List<MetadataResult> getMavenMetadata(String groupId, String artifactId) {
            final DefaultMetadata metadata = new DefaultMetadata(groupId, artifactId, "maven-metadata.xml", Metadata.Nature.RELEASE);
            final List<MetadataRequest> requests = repositories.stream().map(r -> {
                final MetadataRequest metadataRequest = new MetadataRequest();
                metadataRequest.setMetadata(metadata);
                metadataRequest.setRepository(r);
                return metadataRequest;
            }).collect(Collectors.toList());
            final List<MetadataResult> metadataResults = system.resolveMetadata(session, requests);

            if (LOG.isDebugEnabled()) {
                for (MetadataResult metadataResult : metadataResults) {
                    if (metadataResult.getException() != null) {
                        LOG.debug(String.format("Error resolving maven artifact %s:%s %s", groupId, artifactId, metadataResult.getException().getMessage()),
                                metadataResult.getException());
                    }
                }
            }

            return metadataResults;
        }

        private Set<Repository> attemptedRepositories() {
            return repositories.stream()
                    .map(r -> new Repository(r.getId(), r.getUrl()))
                    .collect(Collectors.toSet());
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
    public List<Channel> resolveChannels(List<ChannelCoordinate> channelCoords, List<RemoteRepository> repositories) throws UnresolvedMavenArtifactException, MalformedURLException {
        requireNonNull(channelCoords);

        try (MavenVersionsResolver resolver = create(repositories)) {
            return resolver.resolveChannelMetadata(channelCoords).stream()
                    .map(ChannelMapper::from)
                    .collect(Collectors.toList());
        }
    }
}


