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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.StreamVersionResolver;

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
     * Streams of components that are provides by this channel.
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

    public List<String> getLatestGAVs(StreamVersionResolver resolver) {
        List<String> resolvedGAVs = new ArrayList<>();
        for (Stream stream : resolveStreams()) {
            Optional<String> version = resolver.resolveVersion(stream, getRepositories());
            if (version.isPresent()) {
                resolvedGAVs.add(stream.getGroupId() + ":" + stream.getArtifactId() + ":" + version.get());
            }
        }
        return resolvedGAVs;
    }

    private Collection<Stream> resolveStreams() {
        // streams can have * as groupId or artifactId
        // first thing is to match them to actual groupId and artifactId
        return streams;
    }
}
