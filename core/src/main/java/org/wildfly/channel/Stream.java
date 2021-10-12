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

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.version.FixedVersionMatcher;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.channel.version.VersionPatternMatcher;

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
     * This must be either a single version (e.g. "1.0.0.Final") or a list of comma-separated versions
     * (e.g. "1.0.0.Final, 1.0.1.Final, 1.1.0.Final")
     *
     * Either this field or the versionPattern field must be set.
     */
    private final String version;

    /**
     * VersionPattern of the stream.
     * This is a regular expression that matches any version from this stream (e.g. "2\.2\..*").
     *
     * Either this field or the versionPattern field must be set.
     */
    private final Pattern versionPattern;

    private VersionMatcher versionMatcher;

    @JsonCreator
    Stream(@JsonProperty(value = "groupId", required = true) String groupId,
           @JsonProperty(value = "artifactId", required = true) String artifactId,
           @JsonProperty("version") String version,
           @JsonProperty("version-pattern") Pattern versionPattern) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionPattern = versionPattern;
        validate();
        initVersionComparator();
    }

    private void initVersionComparator() {
        if (version != null) {
            List<String> versions = asList(version.split("[\\s,]+"));
            versionMatcher = new FixedVersionMatcher(versions);
        } else {
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

        if (version != null && versionPattern != null) {
            throw  new IllegalArgumentException(
                    String.format("Invalid stream. only one of version or versionPattern field must be set"));
        }
        if (version == null && versionPattern == null) {
            throw  new IllegalArgumentException(
                    String.format("Invalid stream. Either one of version or versionPattern field must be set"));
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

    public Pattern getVersionPattern() {
        return versionPattern;
    }

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
}
