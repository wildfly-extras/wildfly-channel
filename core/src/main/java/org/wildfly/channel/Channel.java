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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.spi.MavenVersionResolver;

/**
 * Java representation of a Channel.
 */
public class Channel {
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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public List<ChannelRequirement> getChannelRequirements() {
        return channelRequirements;
    }

    public List<MavenRepository> getRepositories() {
        return repositories;
    }

    public Collection<Stream> getStreams() {
        return streams;
    }

    public Optional<String> resolveLatestVersion(String groupId, String artifactId, String extension, String classifier, MavenVersionResolver resolver) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(resolver);

        // first we find if there is a stream for that given (groupId, artifactId).
        Optional<Stream> foundStream = findStreamFor(groupId, artifactId);
        if (!foundStream.isPresent()) {
            return Optional.empty();
        }
        // there is a stream, let's now check its version
        Set<String> versions = resolver.resolve(groupId, artifactId, extension, classifier, repositories, foundStream.get().isResolveWithLocalCache());
        return foundStream.get().getVersionComparator().matches(versions);
    }

    protected Optional<Stream> findStreamFor(String groupId, String artifactId) {
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
}
