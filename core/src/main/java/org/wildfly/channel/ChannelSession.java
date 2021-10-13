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
import static java.util.Optional.empty;
import static org.wildfly.channel.version.VersionMatcher.COMPARATOR;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelSession<T extends MavenVersionsResolver> implements AutoCloseable {
    private List<Channel> channels;
    private final ChannelRecorder recorder = new ChannelRecorder();

    public ChannelSession(List<Channel> channels, MavenVersionsResolver.Factory<T> factory) {
        requireNonNull(channels);
        requireNonNull(factory);
        this.channels = channels;
        for (Channel channel : channels) {
            channel.initResolver(factory);
        }
    }

    public Optional<MavenArtifact> resolveMavenArtifact(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
        requireNonNull(groupId);
        requireNonNull(artifactId);

        // find all latest versions from the different channels;
        Map<String, Channel> found = new HashMap<>();
        for (Channel channel : channels) {
            Optional<Channel.ResolveLatestVersionResult> result = channel.resolveLatestVersion2(groupId, artifactId, extension, classifier, baseVersion);
            if (result.isPresent()) {
                found.put(result.get().version, result.get().channel);
            }
        }

        if (found.isEmpty()) {
            if (baseVersion == null) {
                return empty();
            } else {
                // resolve against the base version
                for (Channel channel : channels) {
                    Optional<Channel.ResolveArtifactResult> artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, baseVersion);
                    if (artifact.isPresent()) {
                        recorder.recordStream(groupId, artifactId, baseVersion, channel);
                    }
                    return Optional.of(new MavenArtifact(groupId, artifactId, extension, classifier, baseVersion, artifact.get().file));
                }
                return empty();
            }
        }

        // compare all latest version from the channels to find the latest overall
        Optional<String> result = found.keySet().stream()
                .sorted(COMPARATOR.reversed())
                .findFirst();
        if (result.isPresent()) {
            Channel channel = found.get(result.get());
            Optional<Channel.ResolveArtifactResult> artifact = channel.resolveArtifact(groupId, artifactId, extension, classifier, result.get());
            if (artifact.isPresent()) {
                recorder.recordStream(groupId, artifactId, result.get(), channel);
            }
            return Optional.of(new MavenArtifact(groupId, artifactId, extension, classifier, result.get(), artifact.get().file));
        }

        return empty();
    }

    @Override
    public void close()  {
        for (Channel channel : channels) {
            channel.close();
        }
    }

    public List<Channel> getRecordedChannels() {
        return recorder.getRecordedChannels();
    }
}
