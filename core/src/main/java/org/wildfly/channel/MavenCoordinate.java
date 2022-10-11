/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Java representation of a Maven Coordinate used to represent a channel artifact.
 */
public class MavenCoordinate {

    /**
     * GroupId of the maven artifact.
     */
    private String groupId;

    /**
     * ArtifactId of the maven artifact.
     */
    private String artifactId;
    /**
     * Optional version of the maven artifact.
     */
    private String version;

    @JsonCreator
    public MavenCoordinate(@JsonProperty(value = "groupId", required = true) String groupId,
                           @JsonProperty(value = "artifactId", required = true) String artifactId,
                           @JsonProperty(value = "version") String version) {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
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

    @Override
    public String toString() {
        return "MavenCoordinate{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenCoordinate that = (MavenCoordinate) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }
}
