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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Objects.requireNonNull;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.FixedVersionMatcher;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.channel.version.VersionPatternMatcher;

/**
 * Java representation of a Stream.
 */
public class Stream implements Comparable<Stream> {
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
     * This must be either a single version (e.g. "1.0.0.Final") or a list of comma-separated versions
     * (e.g. "1.0.0.Final, 1.0.1.Final, 1.1.0.Final")
     *
     * Only one of {@code version}, {@code versionPattern} must be set.
     */
    private final String version;

    /**
     * VersionPattern of the stream.
     * This is a regular expression that matches any version from this stream (e.g. "2\.2\..*").
     *
     * Only one of {@code version}, {@code versionPattern} must be set.
     */
    private final Pattern versionPattern;

    private VersionMatcher versionMatcher;

    @JsonCreator
    Stream(@JsonProperty(value = "groupId", required = true) String groupId,
           @JsonProperty(value = "artifactId", required = true) String artifactId,
           @JsonProperty("version") String version,
           @JsonProperty("versionPattern") Pattern versionPattern) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionPattern = versionPattern;
        validate();
        initVersionMatcher();
    }

    private void initVersionMatcher() {
        if (version != null) {
            versionMatcher = new FixedVersionMatcher(version);
        } else {
            requireNonNull(versionPattern);
            // let's instead find a version matching the pattern
            versionMatcher = new VersionPatternMatcher(versionPattern);
        }
    }

    private void validate() {
        if ("*".equals(groupId)) {
            if (!"*".equals(artifactId)) {
                throw new IllegalArgumentException(
                        String.format("Invalid stream %s:%s. It is not valid to use a * groupId if the artifactId is defined", groupId, artifactId));
            }
        }

        if ((version != null && versionPattern != null) ||
                (version == null && versionPattern == null )) {
            throw new IllegalArgumentException(
                    String.format("Invalid stream. Only one of version, versionPattern field must be set"));
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    @JsonInclude(NON_NULL)
    public String getVersion() {
        return version;
    }

    @JsonInclude(NON_NULL)
    public Pattern getVersionPattern() {
        return versionPattern;
    }

    @JsonIgnore
    public VersionMatcher getVersionComparator() {
        return versionMatcher;
    }

    @Override
    public String toString() {
        return "Stream{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", versionPattern=" + versionPattern +
                ", versionComparator=" + versionMatcher +
                '}';
    }

    /*
     * Sort streams by groupId and then artifactId
     */
    @Override
    public int compareTo(Stream other) {
        int groupIdComp = this.groupId.compareTo(other.groupId);
        if (groupIdComp != 0) {
            return groupIdComp;
        }
        return this.artifactId.compareTo(other.artifactId);
    }
}
