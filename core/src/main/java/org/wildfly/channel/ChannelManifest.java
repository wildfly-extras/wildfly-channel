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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Java representation of a Channel Manifest.
 */
public class ChannelManifest {

    public static final String CLASSIFIER="manifest";
    public static final String EXTENSION="yaml";

    /**
     * Version of the schema used by this manifest.
     * This is a required field.
     */
    private final String schemaVersion;

    /**
     * Optional manifest name.
     * Short, one-line description of the manifest
     */
    private final String name;

    /**
     * Optional manifest ID.
     * Alphanumeric identifier of manifest.
     */
    private final String id;

    /**
     * Optional description of the manifest. It can use multiple lines.
     */
    private final String description;

    /**
     * Streams of components that are provided by this manifest.
     */
    private Set<Stream> streams;

    /**
     * Optional list of manifests that should be checked if artifact cannot be found in this manifest.
     */
    private List<ManifestRequirement> manifestRequirements;

    /**
     * Representation of a ChannelManifest resource using the current schema version.
     *
     * @see #ChannelManifest(String, String, String, String, Collection, Collection)
     */
    public ChannelManifest(String name,
                           String id,
                           String description,
                           Collection<Stream> streams) {
        this(ChannelManifestMapper.CURRENT_SCHEMA_VERSION,
                name,
                id,
                description,
                Collections.emptyList(),
                streams);
    }

    /**
     * Representation of a ChannelManifest resource using the current schema version.
     *
     * @see #ChannelManifest(String, String, String, String, Collection, Collection)
     */
    public ChannelManifest(String name,
                           String id,
                           String description,
                           Collection<ManifestRequirement> manifestRequirements,
                           Collection<Stream> streams) {
        this(ChannelManifestMapper.CURRENT_SCHEMA_VERSION,
                name,
                id,
                description,
                manifestRequirements,
                streams);
    }

    /**
     * Representation of a Channel resource
     *
     * @param schemaVersion the version of the schema to validate this manifest resource - required
     * @param name the name of the manifest - can be {@code null}
     * @param description the description of the manifest - can be {@code null}
     * @param streams the streams defined by the manifest - can be {@code null}
     */
    @JsonCreator
    @JsonPropertyOrder({ "schemaVersion", "name", "description", "streams" })
    public ChannelManifest(@JsonProperty(value = "schemaVersion", required = true) String schemaVersion,
                           @JsonProperty(value = "name") String name,
                           @JsonProperty(value = "id") String id,
                           @JsonProperty(value = "description") String description,
                           @JsonProperty(value = "requires") Collection<ManifestRequirement> manifestRequirements,
                           @JsonProperty(value = "streams") Collection<Stream> streams) {
        this.schemaVersion = schemaVersion;
        this.name = name;
        this.id = id;
        this.description = description;
        this.manifestRequirements = new ArrayList<>();
        if (manifestRequirements != null) {
            this.manifestRequirements.addAll(manifestRequirements);
        }
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
    public String getId() {
        return id;
    }

    @JsonInclude(NON_NULL)
    public String getDescription() {
        return description;
    }

    @JsonInclude(NON_EMPTY)
    @JsonProperty(value = "requires")
    public List<ManifestRequirement> getManifestRequirements() {
        return manifestRequirements;
    }

    @JsonInclude(NON_EMPTY)
    public Collection<Stream> getStreams() {
        return streams;
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
}
