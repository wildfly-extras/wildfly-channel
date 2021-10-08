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

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.FixedVersionComparator;
import org.wildfly.channel.version.VersionComparator;

/**
 * Java representation of a Stream.
 */
public class Stream {
    /**
     * GroupId of the stream.
     * It must be either a valid groupId (corresponding to a G of a Maven GAV) or {@code *} to represent any groupId.
     */
    private final String groupId;

    /**
     * ArtifactId of the stream.
     * It must be either a valid artifactId (corresponding to the A of a Maven GAV) or {@code *} to represent any artifactId.
     */
    private final String artifactId;

    /**
     * Version of the stream.
     * It must be a valid version (corresponding the V of a Maven GAV).
     * This is an optional field.
     */
    private final String version;

    /**
     * Whether the local cache from Maven must be checked to resolve the latest version of this stream.
     * This is an optional field.
     * It is false by default.
     */
    private boolean resolveWithLocalCache;

    private VersionComparator versionComparator;

    @JsonCreator
    Stream(@JsonProperty(value = "groupId", required = true) String groupId,
           @JsonProperty(value = "artifactId", required = true) String artifactId,
           @JsonProperty("version") String version,
           @JsonProperty("resolve-with-local-cache") boolean resolveWithLocalCache) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.resolveWithLocalCache = resolveWithLocalCache;
        validate();
        initVersionComparator();
    }

    private void initVersionComparator() {
        if (version != null) {
            List<String> versions = asList(version.split("[\\s,]+"));
            versionComparator = new FixedVersionComparator(versions);
        } else {
            // let's instead find a version matching the pattern
            versionComparator = new FixedVersionComparator(Collections.emptyList());
        }
    }

    private void validate() {
        if ("*".equals(groupId)) {
            if (!"*".equals(artifactId)) {
                throw new IllegalArgumentException(
                        String.format("Invalid stream %s:%s. It is not valid to use a * groupId if the artifactId is defined", groupId, artifactId));
            }
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public boolean isResolveWithLocalCache() {
        return resolveWithLocalCache;
    }


    public VersionComparator getVersionComparator() {
        return versionComparator;
    }

    @Override
    public String toString() {
        return "Stream{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", resolveWithLocalCache=" + resolveWithLocalCache +
                ", versionComparator=" + versionComparator +
                '}';
    }
}
