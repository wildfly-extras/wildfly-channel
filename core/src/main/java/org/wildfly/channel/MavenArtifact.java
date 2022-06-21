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

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.util.Objects;

public class MavenArtifact extends DefaultArtifactCoordinate {
    private final File file;

    public MavenArtifact(String groupId, String artifactId, String extension, String classifier, String version, File file) {
        super(groupId, artifactId, extension, classifier, version);
        requireNonNull(file);

        this.file = file;
    }

    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return "MavenArtifact{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", extension='" + extension + '\'' +
                ", classifier='" + classifier + '\'' +
                ", version='" + version + '\'' +
                ", file=" + file +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MavenArtifact artifact = (MavenArtifact) o;
        return Objects.equals(file, artifact.file) && Objects.equals(groupId, artifact.groupId) && Objects.equals(artifactId, artifact.artifactId) && Objects.equals(extension, artifact.extension) && Objects.equals(classifier, artifact.classifier) && Objects.equals(version, artifact.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, groupId, artifactId, extension, classifier, version);
    }
}
