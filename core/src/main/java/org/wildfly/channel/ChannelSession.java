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
import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

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

    /**
     * Create a ChannelSession.
     *
     * @param channels the list of channels to resolve Maven artifact
     * @param factory Factory to create {@code MavenVersionsResolver} that are performin the actual Maven resolution.
     */
    public ChannelSession(List<Channel> channels, MavenVersionsResolver.Factory factory) {
        requireNonNull(channels);
        requireNonNull(factory);
        this.channels = channels;
        for (Channel channel : channels) {
            channel.init(factory);
        }
    }

    /**
     * Resolve a Maven Artifact based on the session's channels.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @param version - required
     * @return A resolved Maven Artifact (with a file corresponding to the artifact).
     * @throws UnresolvedMavenArtifactException if the artifact can not be resolved
     */
    public MavenArtifact resolveMavenArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);

        // try to resolve the exact Maven GAV across all channels
        for (Channel channel : channels) {
            try {
                Channel.ResolveArtifactResult artifactResult = channel.resolveArtifact(groupId, artifactId, extension, classifier, version);
                recorder.recordStream(groupId, artifactId, version);
                return new MavenArtifact(groupId, artifactId, extension, classifier, version, artifactResult.file);
            } catch (UnresolvedMavenArtifactException e) {
                // ignore if a channel can not resolve the maven artifact
            }
        }
        throw new UnresolvedMavenArtifactException(String.format("Can not resolve Maven artifact : %s:%s:%s:%s:%s", groupId, artifactId, extension, classifier, version));
    }

    /**
     * Resolve the latest version of the Maven artifact according to the session's channels.
     *
     * @param groupId - required
     * @param artifactId - required
     * @param extension - can be null
     * @param classifier - can be null
     * @return the latest version of a Maven Artifact (with a file corresponding to the artifact).
     * @throws UnresolvedMavenArtifactException if the latest version can not be resolved or the artifact itself can not be resolved
     */
    public MavenArtifact resolveLatestMavenArtifact(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
        Channel.ResolveLatestVersionResult latestVersionResult = getChannelWithLatestVersion(groupId, artifactId, extension, classifier);
        String latestVersion = latestVersionResult.version;
        Channel channel = latestVersionResult.channel;

        Channel.ResolveArtifactResult artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, latestVersion);
        recorder.recordStream(groupId, artifactId, latestVersion);
        return new MavenArtifact(groupId, artifactId, extension, classifier, latestVersion, artifact.file);
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
        return getChannelWithLatestVersion(groupId, artifactId, extension, classifier).version;
    }

    @Override
    public void close()  {
        for (Channel channel : channels) {
            channel.close();
        }
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

    private Channel.ResolveLatestVersionResult getChannelWithLatestVersion(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        // find all latest versions from the different channels;
        Map<String, Channel.ResolveLatestVersionResult> found = new HashMap<>();
        for (Channel channel : channels) {
            Optional<Channel.ResolveLatestVersionResult> result = channel.resolveLatestVersion(groupId, artifactId, extension, classifier);
            if (result.isPresent()) {
                found.put(result.get().version, result.get());
            }
        }

        if (found.isEmpty()) {
            throw new UnresolvedMavenArtifactException(String.format("Can not resolve latest Maven artifact (no stream found) : %s:%s:%s:%s", groupId, artifactId, extension, classifier));
        }

        String latestVersion = found.keySet().stream()
           .sorted(COMPARATOR.reversed())
           .findFirst().get();
        return found.get(latestVersion);
    }
}
