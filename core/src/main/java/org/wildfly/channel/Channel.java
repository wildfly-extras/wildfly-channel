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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

/**
 * Java representation of a Channel.
 */
public class Channel implements AutoCloseable {

    private static final String CLASSIFIER="channel";
    private static final String EXTENSION="yaml";

    /** Version of the schema used by this channel.
     * This is a required field.
     */
    private final String schemaVersion;

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
    private Set<Stream> streams;

    private MavenVersionsResolver resolver;

    /**
     * Representation of a Channel resource using the current schema version.
     *
     * @see #Channel(String, String, Vendor, List, Collection)
     */
    public Channel(String name,
                   String description,
                   Vendor vendor,
                   List<ChannelRequirement> channelRequirements,
                   Collection<Stream> streams) {
        this(ChannelMapper.CURRENT_SCHEMA_VERSION,
                name,
                description,
                vendor,
                channelRequirements,
                streams);
    }

    /**
     * Representation of a Channel resource
     *
     * @param schemaVersion the version of the schema to validate this channel resource - required
     * @param name the name of the channel - can be {@code null}
     * @param description the description of the channel - can be {@code null}
     * @param vendor the vendor of the channel - can be {@code null}
     * @param channelRequirements the required channels - cane be {@code null}
     * @param streams the streams defined by the channel - can be {@code null}
     */
    @JsonCreator
    @JsonPropertyOrder({ "schemaVersion", "name", "description", "vendor", "requires", "streams" })
    public Channel(@JsonProperty(value = "schemaVersion", required = true) String schemaVersion,
                   @JsonProperty(value = "name") String name,
                   @JsonProperty(value = "description") String description,
                   @JsonProperty(value = "vendor") Vendor vendor,
                   @JsonProperty(value = "requires")
                   @JsonInclude(NON_EMPTY) List<ChannelRequirement> channelRequirements,
                   @JsonProperty(value = "streams") Collection<Stream> streams) {
        this.schemaVersion = schemaVersion;
        this.name = name;
        this.description = description;
        this.vendor = vendor;
        this.channelRequirements = (channelRequirements != null) ? channelRequirements : emptyList();
        this.streams = new TreeSet<>();
        if (streams != null) {
            this.streams.addAll(streams);
        }
    }

    @JsonInclude
    public String getSchemaVersion() {
        return schemaVersion;
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
    @JsonProperty(value = "requires")
    public List<ChannelRequirement> getChannelRequirements() {
        return channelRequirements;
    }

    @JsonInclude(NON_EMPTY)
    public Collection<Stream> getStreams() {
        return streams;
    }

    void addStream(Stream stream) {
        Objects.requireNonNull(stream);
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
            if (version == null) {
                Set<String> versions = resolver.getAllVersions(groupId, artifactId, EXTENSION, CLASSIFIER);
                Optional<String> latest = VersionMatcher.getLatestVersion(versions);
                version = latest.orElseThrow(() -> new RuntimeException(String.format("Can not determine the latest version for Maven artifact %s:%s:%s:%s",
                        groupId, artifactId, EXTENSION, CLASSIFIER)));
            }
            try {
                final File file;
                file = resolver.resolveArtifact(groupId, artifactId, EXTENSION, CLASSIFIER, version);
                Channel requiredChannel = ChannelMapper.from(file.toURI().toURL());
                requiredChannel.init(factory);
                requiredChannels.add(requiredChannel);
            } catch (UnresolvedMavenArtifactException | MalformedURLException e) {
                throw new RuntimeException(String.format("Unable to resolve required channel %s:%s:%s", groupId, artifactId, version));
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
            foundVersion = Optional.of(stream.getVersion());
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
        for (Channel requiredChannel : requiredChannels) {
            try {
                return requiredChannel.resolveArtifact(groupId, artifactId, extension, classifier, version);
            } catch (UnresolvedMavenArtifactException e) {
                // ignore if the required channel are not able to resolve the artifact
            }
        }

        return new ResolveArtifactResult(resolver.resolveArtifact(groupId, artifactId, extension, classifier, version), this);
    }

    List<ResolveArtifactResult> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        final List<File> resolvedArtifacts = resolver.resolveArtifacts(coordinates);
        return resolvedArtifacts.stream().map(f->new ResolveArtifactResult(f, this)).collect(Collectors.toList());
    }

    public Optional<Stream> findStreamFor(String groupId, String artifactId) {
        // first exact match:
        Optional<Stream> stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals(artifactId)).findFirst();
        if (stream.isPresent()) {
            return stream;
        }
        // check if there is a stream for groupId:*
        stream = streams.stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals("*")).findFirst();
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
