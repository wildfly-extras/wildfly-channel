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

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.util.List;
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
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @return the Maven Artifact (with a file corresponding to the artifact).
     * @throws UnresolvedMavenArtifactException if the latest version can not be resolved or the artifact itself can not be resolved
     */
    public MavenArtifact resolveMavenArtifact(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
        Channel.ResolveLatestVersionResult result = findChannelWithLatestVersion(groupId, artifactId, extension, classifier);
        String latestVersion = result.version;
        Channel channel = result.channel;

        Channel.ResolveArtifactResult artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, latestVersion);
        recorder.recordStream(groupId, artifactId, latestVersion);
        return new MavenArtifact(groupId, artifactId, extension, classifier, latestVersion, artifact.file);
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
     * Find the latest version of the Maven artifact in the session's channel. The artifact file will not be resolved.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @return the latest version if a Maven artifact
     * @throws UnresolvedMavenArtifactException if the latest version cannot be established
     */
    public String findLatestMavenArtifactVersion(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
        return findChannelWithLatestVersion(groupId, artifactId, extension, classifier).version;
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

    private Channel.ResolveLatestVersionResult findChannelWithLatestVersion(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
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
}
