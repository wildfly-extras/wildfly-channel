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
import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import org.wildfly.channel.version.FixedVersionMatcher;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.channel.version.VersionPatternMatcher;

/**
 * Java representation of a Stream.
 */
public class Stream implements Comparable<Stream> {
    /**
     * GroupId of the stream.
     */
    private final String groupId;

    /**
     * ArtifactId of the stream.
     * It must be either a valid artifactId (corresponding to the A of a Maven GAV) or {@code *} to represent any artifactId.
     */
    private final String artifactId;

    /**
     * Version of the stream.
     * This must be a single version (e.g. "1.0.0.Final").
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

    /**
     * Map of [classifier/]extension to SHA256 checksum.
     */
    private final Map<String, String> sha256Checksum;

    private VersionMatcher versionMatcher;

    /**
     * @see #Stream(String, String, String, Pattern, Map)
     */
    public Stream(String groupId,
                  String artifactId,
                  String version) {
        this(groupId, artifactId, version, null, null);
    }

    /**
     * @see #Stream(String, String, String, Pattern, Map)
     */
    public Stream(String groupId,
                  String artifactId,
                  Pattern versionPattern) {
        this(groupId, artifactId, null, versionPattern, null);
    }

    /**
     * Representation of a stream resource
     *
     * @param groupId groupId of the Maven coordinate - required
     * @param artifactId artifactId of the Maven coordinate - required
     * @param version version of the Maven coordinate - can be {@code null}
     * @param versionPattern version patter to determine the latest version of the resource - can be {@code null}
     * @param sha256Checksum the SHA-256 checksum of the artifacts from this stream - can be {@code null}
     *
     * Either {@code version} or {@code versionPattern} must be defined.
     */
    @JsonCreator
    public Stream(@JsonProperty(value = "groupId", required = true) String groupId,
           @JsonProperty(value = "artifactId", required = true) String artifactId,
           @JsonProperty("version") String version,
           @JsonProperty("versionPattern") Pattern versionPattern,
           @JsonProperty("sha256checksum") Map<String, String> sha256Checksum) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionPattern = versionPattern;
        this.sha256Checksum = sha256Checksum == null ? new HashMap<>() : sha256Checksum;
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
            throw new IllegalArgumentException(
                    String.format("Invalid stream. the groupId does not accept wildcard '*'"));
        }

        if ((version != null && versionPattern != null) ||
                (version == null && versionPattern == null )) {
            throw new IllegalArgumentException(
                    String.format("Invalid stream. Only one of version, versionPattern field must be set"));
        }

        if (!sha256Checksum.isEmpty() && version == null) {
            throw new IllegalArgumentException(
                    String.format("Invalid stream. SHA-256 checksums can only be set when version is used."));
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

    @JsonProperty("sha256checksum")
    @JsonInclude(NON_EMPTY)
    public Map<String, String> getsha256Checksum() {
        return sha256Checksum;
    }

    @Override
    public String toString() {
        return "Stream{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", versionPattern=" + versionPattern +
                ", versionComparator=" + versionMatcher +
                ", sha256Checksum" + sha256Checksum +
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Stream stream = (Stream) o;
        return groupId.equals(stream.groupId) && artifactId.equals(stream.artifactId) && version.equals(stream.version) && Objects.equals(versionPattern, stream.versionPattern) && Objects.equals(sha256Checksum, stream.sha256Checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, versionPattern, sha256Checksum);
    }
}
