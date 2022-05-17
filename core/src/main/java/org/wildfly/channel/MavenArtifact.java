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

public class MavenArtifact implements ArtifactCoordinate {

    private final String groupId;
    private final String artifactId;
    private final String extension;
    private final String classifier;
    private final String version;
    private final File file;

    public MavenArtifact(String groupId, String artifactId, String extension, String classifier, String version, File file) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);
        requireNonNull(file);

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.extension = extension;
        this.classifier = classifier;
        this.version = version;
        this.file = file;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getExtension() {
        return extension;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getVersion() {
        return version;
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
}
