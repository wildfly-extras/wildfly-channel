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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Java representation of a Channel.
 */
public class Channel implements AutoCloseable {
    /**
     * Name of the channel (as an one-line human readable description of the channel).
     * This is an optional field.
     */
    private final String name;

    /**
     * Description of the channel. It can use multiple lines.
     * This is an optional field.
     */
    private final String description;

    /**
     * Vendor of the channel.
     * This is an optional field.
     */
    private final Vendor vendor;

    /**
     * Other channels that are required by the channel.
     * This is an optional field.
     */
    private List<ChannelRequirement> channelRequirements;

    /**
     * Required channels
     */
    private List<Channel> requiredChannels = Collections.emptyList();

    /**
     * Streams of components that are provided by this channel.
     */
    private Collection<Stream> streams;

    private MavenVersionsResolver resolver;

    public Channel(@JsonProperty(value = "name") String name,
                   @JsonProperty(value = "description") String description,
                   @JsonProperty(value = "vendor") Vendor vendor,
                   @JsonProperty(value = "requires")
                   @JsonInclude(NON_EMPTY) List<ChannelRequirement> channelRequirements,
                   @JsonProperty(value = "streams") Collection<Stream> streams) {
        this.name = name;
        this.description = description;
        this.vendor = vendor;
        this.channelRequirements = (channelRequirements != null) ? channelRequirements : emptyList();
        this.streams = (streams != null) ? streams : emptyList();
    }

    @JsonInclude(NON_NULL)
    public String getName() {
        return name;
    }

    @JsonInclude(NON_NULL)
    public String getDescription() {
        return description;
    }

    @JsonInclude(NON_NULL)
    public Vendor getVendor() {
        return vendor;
    }

    @JsonInclude(NON_EMPTY)
    public List<ChannelRequirement> getChannelRequirements() {
        return channelRequirements;
    }

    @JsonInclude(NON_EMPTY)
    public Collection<Stream> getStreams() {
        return streams;
    }

    void addStream(Stream stream) {
        Objects.requireNonNull(stream);
        this.streams = new ArrayList<>(streams);
        this.streams.add(stream);
    }

    void init(MavenVersionsResolver.Factory factory) {
        resolver = factory.create();

        if (!channelRequirements.isEmpty()) {
            requiredChannels = new ArrayList<>();
        }
        for (ChannelRequirement channelRequirement : channelRequirements) {
            String groupId = channelRequirement.getGroupId();
            String artifactId = channelRequirement.getArtifactId();
            String version = channelRequirement.getVersion();
            try {
                final File file;
                if (version != null) {
                    file = resolver.resolveArtifact(groupId, artifactId, "yaml", "channel", version);
                } else {
                    file = resolver.resolveLatestVersionFromMavenMetadata(groupId, artifactId, "yaml", "channel");
                }
                Channel requiredChannel = ChannelMapper.from(file.toURI().toURL());
                requiredChannel.init(factory);
                requiredChannels.add(requiredChannel);
            } catch (UnresolvedMavenArtifactException | MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        for (Channel requiredChannel : requiredChannels) {
            requiredChannel.close();
        }
        this.resolver.close();
        this.resolver = null;
    }

    static class ResolveLatestVersionResult {
        final String version;
        final Channel channel;

        ResolveLatestVersionResult(String version, Channel channel) {
            this.version = version;
            this.channel = channel;
        }
    }


    Optional<ResolveLatestVersionResult> resolveLatestVersion(String groupId, String artifactId, String extension, String classifier) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(resolver);

        // first we find if there is a stream for that given (groupId, artifactId).
        Optional<Stream> foundStream = findStreamFor(groupId, artifactId);

        // no stream for this artifact, let's look into the required channel
        if (!foundStream.isPresent()) {
            // we return the latest value from the required channels
            Map<String, Channel> foundVersions = new HashMap<>();
            for (Channel requiredChannel : requiredChannels) {
                Optional<Channel.ResolveLatestVersionResult> found = requiredChannel.resolveLatestVersion(groupId, artifactId, extension, classifier);
                if (found.isPresent()) {
                    foundVersions.put(found.get().version, found.get().channel);
                }
            }
            Optional<String> foundVersionInRequiredChannels = foundVersions.keySet().stream().sorted(VersionMatcher.COMPARATOR.reversed()).findFirst();
            if (foundVersionInRequiredChannels.isPresent()) {
                return Optional.of(new ResolveLatestVersionResult(foundVersionInRequiredChannels.get(), foundVersions.get(foundVersionInRequiredChannels.get())));
            }
            return Optional.empty();
        }

        Stream stream = foundStream.get();
        Optional<String> foundVersion = Optional.empty();
        // there is a stream, let's now check its version
        if (stream.getVersion() != null) {
            foundVersion = Arrays.stream(stream.getVersion().split("[\\s,]+"))
                    .sorted(VersionMatcher.COMPARATOR.reversed())
                    .findFirst();
        } else if (stream.getVersionPattern() != null) {
            // if there is a version pattern, we resolve all versions from Maven to find the latest one
            Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
            foundVersion = foundStream.get().getVersionComparator().matches(versions);
        }

        if (foundVersion.isPresent()) {
            return Optional.of(new ResolveLatestVersionResult(foundVersion.get(), this));
        }
        return Optional.empty();
    }


    static class ResolveArtifactResult {
        File file;
        Channel channel;

        ResolveArtifactResult(File file, Channel channel) {
            this.file = file;
            this.channel = channel;
        }
    }

    ResolveArtifactResult resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);
        requireNonNull(resolver);

        // first we looked into the required channels
        ResolveArtifactResult resultFromChannelRequirements = null;
        for (Channel requiredChannel : requiredChannels) {
            try {
                return requiredChannel.resolveArtifact(groupId, artifactId, extension, classifier, version);
            } catch (UnresolvedMavenArtifactException e) {
            }
        }

        return new ResolveArtifactResult(resolver.resolveArtifact(groupId, artifactId, extension, classifier, version), this);
    }

    Optional<Stream> findStreamFor(String groupId, String artifactId) {
        // first exact match:
        Optional<Stream> stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals(artifactId)).findFirst();
        if (stream.isPresent()) {
            return stream;
        }
        // check if there is a stream for groupId:*
        stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals("*")).findFirst();
        if (stream.isPresent()) {
            return stream;
        }
        // finally check if there is a stream for *:*
        stream = streams.stream().filter(s -> s.getGroupId().equals("*") && s.getArtifactId().equals("*")).findFirst();
        return stream;
    }

    @Override
    public String toString() {
        return "Channel{" +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", vendor=" + vendor +
                ", channelRequirements=" + channelRequirements +
                ", streams=" + streams +
                '}';
    }
}
