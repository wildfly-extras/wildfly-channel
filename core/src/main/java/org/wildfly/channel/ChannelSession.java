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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.wildfly.channel.spi.MavenVersionsResolver;

/**
 * A ChannelSession is used to install and resolve Maven Artifacts inside a single scope.
 */
public class ChannelSession implements AutoCloseable {
    private final List<Channel> channels;
    private final ChannelRecorder recorder = new ChannelRecorder();
    private final MavenVersionsResolver resolver;

    /**
     * Create a ChannelSession.
     *
     * @param channels the list of channels to resolve Maven artifact
     * @param factory Factory to create {@code MavenVersionsResolver} that are performing the actual Maven resolution.
     */
    public ChannelSession(List<Channel> channels, MavenVersionsResolver.Factory factory) {
        requireNonNull(channels);
        requireNonNull(factory);
        this.resolver = factory.create();
        this.channels = channels;
        for (Channel channel : channels) {
            channel.init(factory);
        }
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

        Channel.ResolveLatestVersionResult result = findChannelWithLatestVersion(groupId, artifactId, extension, classifier, baseVersion);
        String latestVersion = result.version;
        Channel channel = result.channel;

        Channel.ResolveArtifactResult artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, latestVersion);
        recorder.recordStream(groupId, artifactId, latestVersion);
        return new MavenArtifact(groupId, artifactId, extension, classifier, latestVersion, artifact.file);
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

        Map<Channel, List<ArtifactCoordinate>> channelMap = splitArtifactsPerChannel(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (Channel channel : channelMap.keySet()) {
            final List<ArtifactCoordinate> requests = channelMap.get(channel);
            final List<Channel.ResolveArtifactResult> resolveArtifactResults = channel.resolveArtifacts(requests);
            for (int i = 0; i < requests.size(); i++) {
                final ArtifactCoordinate request = requests.get(i);
                final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(), request.getExtension(), request.getClassifier(), request.getVersion(), resolveArtifactResults.get(i).file);

                recorder.recordStream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion());
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
    public MavenArtifact resolveDirectMavenArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);

        File file = resolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        recorder.recordStream(groupId, artifactId, version);
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
    public List<MavenArtifact> resolveDirectMavenArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        coordinates.stream().forEach(c->{
            requireNonNull(c.getGroupId());
            requireNonNull(c.getArtifactId());
            requireNonNull(c.getVersion());
        });
        final List<File> files = resolver.resolveArtifacts(coordinates);

        final ArrayList<MavenArtifact> res = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            final ArtifactCoordinate request = coordinates.get(i);
            final MavenArtifact resolvedArtifact = new MavenArtifact(request.getGroupId(), request.getArtifactId(), request.getExtension(), request.getClassifier(), request.getVersion(), files.get(i));

            recorder.recordStream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion());
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
        for (Channel channel : channels) {
            channel.close();
        }
        resolver.close();
    }

    /**
     * Returns a synthetic Channel where each resolved artifacts (either with exact or latest version)
     * is defined in a {@code Stream} with a {@code version} field.
     *
     * This channel can be used to reproduce the same resolution in another ChannelSession.
     *
     * @return a synthetic Channel.
     */
    public Channel getRecordedChannel() {
        return recorder.getRecordedChannel();
    }

    private Channel.ResolveLatestVersionResult findChannelWithLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        for (Channel channel : channels) {
            Optional<Channel.ResolveLatestVersionResult> result = channel.resolveLatestVersion(groupId, artifactId, extension, classifier);
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw new UnresolvedMavenArtifactException(String.format("Can not resolve latest Maven artifact (no stream found) : %s:%s:%s:%s", groupId, artifactId, extension, classifier));
    }

    private Map<Channel, List<ArtifactCoordinate>> splitArtifactsPerChannel(List<ArtifactCoordinate> coordinates) {
        Map<Channel, List<ArtifactCoordinate>> channelMap = new HashMap<>();
        for (ArtifactCoordinate coord : coordinates) {
            Channel.ResolveLatestVersionResult result = findChannelWithLatestVersion(coord.getGroupId(), coord.getArtifactId(),
                                                                                     coord.getExtension(), coord.getClassifier(), coord.getVersion());
            ArtifactCoordinate query = new ArtifactCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier(), result.version);
            Channel channel = result.channel;
            if (!channelMap.containsKey(channel)) {
                channelMap.put(channel, new ArrayList<>());
            }
            channelMap.get(channel).add(query);
        }
        return channelMap;
    }
}
