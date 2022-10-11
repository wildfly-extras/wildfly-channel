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
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Java representation of a Channel requirement identified by Maven coordinates.
 */
public class ManifestRequirement {

    private final String id;
    private final MavenCoordinate mavenCoordinate;

    /**
     * Representation of a channel requirement.
     *
     * @param id - ID of the required manifest
     * @param mavenCoordinate - optional {@link MavenCoordinate} of the required manifest
    */
    @JsonCreator
    public ManifestRequirement(@JsonProperty(value = "id", required = true) String id,
                               @JsonProperty(value = "maven") MavenCoordinate mavenCoordinate) {
        requireNonNull(id);

        this.id = id;
        this.mavenCoordinate = mavenCoordinate;
    }

    public String getId() {
        return id;
    }

    @JsonInclude(NON_EMPTY)
    @JsonProperty("maven")
    public MavenCoordinate getMavenCoordinate() {
        return mavenCoordinate;
    }

    @JsonIgnore
    public String getGroupId() {
        return mavenCoordinate == null?null:mavenCoordinate.getGroupId();
    }

    @JsonIgnore
    public String getArtifactId() {
        return mavenCoordinate == null?null:mavenCoordinate.getArtifactId();
    }

    @JsonIgnore
    public String getVersion() {
        return mavenCoordinate == null?null:mavenCoordinate.getVersion();
    }

    @Override
    public String toString() {
        return "ManifestRequirement{" +
                "id='" + id + '\'' +
                ", mavenCoordinate=" + mavenCoordinate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ManifestRequirement that = (ManifestRequirement) o;
        return Objects.equals(id, that.id) && Objects.equals(mavenCoordinate, that.mavenCoordinate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, mavenCoordinate);
    }
}
