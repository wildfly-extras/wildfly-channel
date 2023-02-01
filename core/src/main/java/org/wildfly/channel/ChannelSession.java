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

import static java.util.Objects.requireNonNull;
import static org.wildfly.channel.ChecksumUtil.computeSHA256;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * A ChannelSession is used to install and resolve Maven Artifacts inside a single scope.
 */
public class ChannelSession implements AutoCloseable {
    private final List<ChannelImpl> channels;
    private final ChannelRecorder recorder = new ChannelRecorder();
    // resolver used for direct dependencies only. Uses combination of all repositories in the channels.
    private final MavenVersionsResolver combinedResolver;

    /**
     * Create a ChannelSession.
     *
     * @param channelDefinitions the list of channels to resolve Maven artifact
     * @param factory Factory to create {@code MavenVersionsResolver} that are performing the actual Maven resolution.
     * @throws UnresolvedRequiredManifestException - if a required manifest cannot be resolved either via maven coordinates or in the list of channels
     * @throws CyclicDependencyException - if the required manifests form a cyclic dependency
     */
    public ChannelSession(List<Channel> channelDefinitions, MavenVersionsResolver.Factory factory) {
        requireNonNull(channelDefinitions);
        requireNonNull(factory);

        final Set<Repository> repositories = channelDefinitions.stream().flatMap(c -> c.getRepositories().stream()).collect(Collectors.toSet());
        this.combinedResolver = factory.create(repositories);

        List<ChannelImpl> channelList = channelDefinitions.stream().map(ChannelImpl::new).collect(Collectors.toList());
        for (ChannelImpl channel : channelList) {
            channel.init(factory, channelList);
        }
        // filter out channels marked as dependency, so that resolution starts only at top level channels
        this.channels = channelList.stream().filter(c->!c.isDependency()).collect(Collectors.toList());

        validateNoDuplicatedManifests();
    }

    /**
     * Resolve the Maven artifact according to the session's channels.
     *
     * In order to find the stream corresponding to the Maven artifact, the channels are searched depth-first, starting
     * with the first channel in the list and into their respective required channels.
     * Once the first stream that matches the {@code groupId} and {@artifactId} parameters is found, the Maven artifact
     * will be resolved with the version determined by this stream.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param baseVersion - can be null. The base version is required when the stream for the component specifies multiple versions and needs the base version to
     *                    determine the appropriate version to resolve.
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws UnresolvedMavenArtifactException if the latest version can not be resolved or the artifact itself can not be resolved.
     *
     */
    public MavenArtifact resolveMavenArtifact(String groupId, String artifactId, String extension, String classifier, String baseVersion) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        // baseVersion is not used at the moment but will provide essential to support advanced use cases to determine multiple streams of the same Maven component.

        ChannelImpl.ResolveLatestVersionResult result = findChannelWithLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
        String latestVersion = result.version;
        ChannelImpl channel = result.channel;
        Stream stream = result.stream;

        ChannelImpl.ResolveArtifactResult artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, latestVersion);
        // verify the integrity of the artifact file.
        String sha256 = verifySHA256Checksum(stream, artifact.file, extension, classifier);
        recorder.recordStream(groupId, artifactId, latestVersion, extension, classifier, sha256);
        return new MavenArtifact(groupId, artifactId, extension, classifier, latestVersion, artifact.file);
    }

    private String verifySHA256Checksum(Stream stream, File artifactFile, String extension, String classifier) {
        String sha256;
        try {
            sha256 = computeSHA256(artifactFile);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to compute the SHA-256 checksum of %s", artifactFile));
        }
        if (stream == null) {
            return sha256;
        }
        if (extension != null) {
            String key = extension;
            if (classifier != null && !classifier.isEmpty()) {
                key = classifier + "/" + extension;
            }
            if (stream.getsha256Checksum().containsKey(key)) {
                String expectedsha256 = stream.getsha256Checksum().get(key);
                if (!sha256.equalsIgnoreCase(expectedsha256)) {
                    throw new RuntimeException(String.format("Integrity of the file %s is not correct, SHA-256 sum does not match (expected: %s, computed: %s)",
                            artifactFile.getAbsolutePath(), expectedsha256, sha256));
                }
            }
        }
        return sha256;
    }

    /**
     * Resolve a list of Maven artifacts according to the session's channels.
     *
     * In order to find the stream corresponding to the Maven artifact, the channels are searched depth-first, starting
     * with the first channel in the list and into their respective required channels.
     * Once the first stream that matches the {@code groupId} and {@artifactId} parameters is found, the Maven artifact
     * will be resolved with the version determined by this stream.
     *
     * The returned list of resolved artifacts does not maintain ordering of requested coordinates
     *
     * @param coordinates list of ArtifactCoordinates to resolve
     * @return a list of resolved MavenArtifacts with resolved versions asnd files
     * @throws UnresolvedMavenArtifactException
     */
    public List<MavenArtifact> resolveMavenArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        requireNonNull(coordinates);

        Map<ChannelImpl, List<ArtifactCoordinate>> channelMap = splitArtifactsPerChannel(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (ChannelImpl channel : channelMap.keySet()) {
            final List<ArtifactCoordinate> requests = channelMap.get(channel);
            final List<ChannelImpl.ResolveArtifactResult> resolveArtifactResults = channel.resolveArtifacts(requests);
            for (int i = 0; i < requests.size(); i++) {
                final ArtifactCoordinate request = requests.get(i);
                final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(), request.getExtension(), request.getClassifier(), request.getVersion(), resolveArtifactResults.get(i).file);
                // verify the integrity of the artifact file.
                Optional<Stream > stream = channel.getManifest().findStreamFor(request.getGroupId(), request.getArtifactId());
                String sha256 = verifySHA256Checksum(stream.get(), resolvedArtifact.getFile(), resolvedArtifact.getExtension(), resolvedArtifact.getClassifier());
                recorder.recordStream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion(), resolvedArtifact.getExtension(), resolvedArtifact.getClassifier(), sha256);
                res.add(resolvedArtifact);
            }
        }
        return res;
    }

    /**
     * Resolve the Maven artifact with a specific version without checking the channels.
     *
     * If the artifact is resolved, a stream for it is added to the {@code getRecordedChannel}.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param version - required
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws UnresolvedMavenArtifactException if the artifact can not be resolved
     */
    public MavenArtifact resolveDirectMavenArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException, IOException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);

        File file = combinedResolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        recorder.recordStream(groupId, artifactId, version, extension, classifier, computeSHA256(file));
        return new MavenArtifact(groupId, artifactId, extension, classifier, version, file);
    }

    /**
     * Resolve a list of Maven artifacts with a specific version without checking the channels.
     *
     * If the artifact is resolved, a stream for it is added to the {@code getRecordedChannel}.
     *
     * @param coordinates - list of ArtifactCoordinates to check
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws UnresolvedMavenArtifactException if the artifact can not be resolved
     */
    public List<MavenArtifact> resolveDirectMavenArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException, IOException {
        coordinates.stream().forEach(c->{
            requireNonNull(c.getGroupId());
            requireNonNull(c.getArtifactId());
            requireNonNull(c.getVersion());
        });
        final List<File> files = combinedResolver.resolveArtifacts(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            final ArtifactCoordinate request = coordinates.get(i);
            final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(), request.getExtension(), request.getClassifier(), request.getVersion(), files.get(i));

            recorder.recordStream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion(), resolvedArtifact.getExtension(), resolvedArtifact.getClassifier(), computeSHA256(resolvedArtifact.getFile()));
            res.add(resolvedArtifact);
        }
        return res;
    }

    /**
     * Find the latest version of the Maven artifact in the session's channel. The artifact file will not be resolved.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param baseVersion - can be null. The base version is required when the stream for the component specifies multiple versions and needs the base version to
     *                    determine the appropriate version to resolve.
     * @return the latest version if a Maven artifact
     * @throws UnresolvedMavenArtifactException if the latest version cannot be established
     */
    public String findLatestMavenArtifactVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) throws UnresolvedMavenArtifactException {
        return findChannelWithLatestVersion(groupId, artifactId, extension, classifier, baseVersion).version;
    }

    @Override
    public void close()  {
        for (ChannelImpl channel : channels) {
            channel.close();
        }
        combinedResolver.close();
    }

    /**
     * Returns a synthetic Channel where each resolved artifacts (either with exact or latest version)
     * is defined in a {@code Stream} with a {@code version} field.
     *
     * This channel can be used to reproduce the same resolution in another ChannelSession.
     *
     * @return a synthetic Channel.
     */
    public ChannelManifest getRecordedChannel() {
        return recorder.getRecordedChannel();
    }

    private void validateNoDuplicatedManifests() {
        final List<String> manifestIds = this.channels.stream().map(c -> c.getManifest().getId()).filter(id -> id != null).collect(Collectors.toList());
        if (manifestIds.size() != new HashSet<>(manifestIds).size()) {
            throw new RuntimeException("The same manifest is provided by one or more channels");
        }
    }

    private ChannelImpl.ResolveLatestVersionResult findChannelWithLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        Map<String, ChannelImpl.ResolveLatestVersionResult> foundVersions = new HashMap<>();
        for (ChannelImpl channel : channels) {
            Optional<ChannelImpl.ResolveLatestVersionResult> result = channel.resolveLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
            if (result.isPresent()) {
                foundVersions.put(result.get().version, result.get());
            }
        }

        // find the latest version from all the channels that defined the stream.
        Optional<String> foundLatestVersionInChannels = foundVersions.keySet().stream().sorted(VersionMatcher.COMPARATOR.reversed()).findFirst();
        return foundVersions.get(foundLatestVersionInChannels.orElseThrow(() -> {
            throw new UnresolvedMavenArtifactException(String.format("Can not resolve latest Maven artifact (no stream found) : %s:%s:%s:%s", groupId, artifactId, extension, classifier));
        }));
    }

    private Map<ChannelImpl, List<ArtifactCoordinate>> splitArtifactsPerChannel(List<ArtifactCoordinate> coordinates) {
        Map<ChannelImpl, List<ArtifactCoordinate>> channelMap = new HashMap<>();
        for (ArtifactCoordinate coord : coordinates) {
            ChannelImpl.ResolveLatestVersionResult result = findChannelWithLatestVersion(coord.getGroupId(), coord.getArtifactId(),
                                                                                     coord.getExtension(), coord.getClassifier(), coord.getVersion());
            ArtifactCoordinate query = new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), result.version);
            ChannelImpl channel = result.channel;
            if (!channelMap.containsKey(channel)) {
                channelMap.put(channel, new ArrayList<>());
            }
            channelMap.get(channel).add(query);
        }
        return channelMap;
    }
}
