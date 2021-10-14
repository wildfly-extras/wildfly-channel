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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Java representation of a Channel.
 */
public class Channel implements AutoCloseable {
    /**
     * Identifier of the channel.
     * This is an optional field.
     */
    private String id;

    /**
     * Name of the channel (as an one-line human readable description of the channel).
     * This is an optional field.
     */
    private String name;

    /**
     * Description of the channel. It can use multiple lines.
     * This is an optional field.
     */
    private String description;

    /**
     * Vendor of the channel.
     * This is an optional field.
     */
    private Vendor vendor;

    /**
     * Whether the local cache from Maven must be checked to resolve the latest versions from this channel.
     * This is an optional field.
     * It is false by default.
     */
    @JsonProperty("resolveWithLocalCache")
    private boolean resolveWithLocalCache;

    /**
     * Other channels that are required by the channel.
     * This is an optional field.
     */
    @JsonProperty("requires")
    private List<ChannelRequirement> channelRequirements = emptyList();

    /**
     * Maven repositories that contains all artifacts from this channel.
     * This is an optional field.
     */
    private List<MavenRepository> repositories = emptyList();

    /**
     * Streams of components that are provided by this channel.
     */
    private Collection<Stream> streams = emptySet();

    private MavenVersionsResolver resolver;
    private MavenVersionsResolver.Factory factory;

    @JsonInclude(NON_NULL)
    public String getId() {
        return id;
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

    void setResolveWithLocalCache(boolean resolveWithLocalCache) {
        this.resolveWithLocalCache = resolveWithLocalCache;
    }

    @JsonInclude(NON_DEFAULT)
    public boolean isResolveWithLocalCache() {
        return resolveWithLocalCache;
    }

    @JsonInclude(NON_EMPTY)
    public List<MavenRepository> getRepositories() {
        return repositories;
    }

    void setRepositories(List<MavenRepository> repositories) {
        Objects.requireNonNull(repositories);
        this.repositories = repositories;
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

    void initResolver(MavenVersionsResolver.Factory factory) {
        this.factory = factory;
        resolver = factory.create(repositories, resolveWithLocalCache);

    }

    @Override
    public void close() {
        this.resolver.close();
        this.resolver = null;
        this.factory = null;
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

        Map<String, Channel> foundVersions = new HashMap<>();
        List<Channel> requiredChannels = channelRequirements.stream().map(cr -> ChannelMapper.from(cr.getURL())).collect(Collectors.toList());
        for (Channel requiredChannel : requiredChannels) {
            requiredChannel.initResolver(factory);
            Optional<Channel.ResolveLatestVersionResult> found = requiredChannel.resolveLatestVersion(groupId, artifactId, extension, classifier);
            if (found.isPresent()) {
                foundVersions.put(found.get().version, found.get().channel);
            }
            requiredChannel.close();
        }
        Optional<String> foundVersionInRequiredChannels = foundVersions.keySet().stream().sorted(VersionMatcher.COMPARATOR.reversed()).findFirst();
        Optional<ResolveLatestVersionResult> resultFromRequiredChannels = Optional.empty();
        if (foundVersionInRequiredChannels.isPresent()) {
            resultFromRequiredChannels = Optional.of(new ResolveLatestVersionResult(foundVersionInRequiredChannels.get(), foundVersions.get(foundVersionInRequiredChannels.get())));
        }

        // first we find if there is a stream for that given (groupId, artifactId).
        Optional<Stream> foundStream = findStreamFor(groupId, artifactId);
        if (!foundStream.isPresent()) {
            // we return any result from the required channel
            return resultFromRequiredChannels;
        }

        // there is a stream, let's now check its version
        Set<String> versions = resolver.getAllVersions(groupId, artifactId, extension, classifier);
        Optional<String> foundVersion = foundStream.get().getVersionComparator().matches(versions);
        // if a version is found in this channel, it always wins against any stream in its required channel
        if (foundVersion.isPresent()) {
            return Optional.of(new ResolveLatestVersionResult(foundVersion.get(), this));
        } else {
            // we return any result from the required channel
            return resultFromRequiredChannels;
        }
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

        // first we looked into the required channels
        List<Channel> requiredChannels = channelRequirements.stream().map(cr -> ChannelMapper.from(cr.getURL())).collect(Collectors.toList());
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
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", vendor=" + vendor +
                ", resolveWithLocalCache=" + resolveWithLocalCache +
                ", channelRequirements=" + channelRequirements +
                ", repositories=" + repositories +
                ", streams=" + streams +
                '}';
    }
}
