/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.channel.spi;

import java.net.URL;

import org.wildfly.channel.ArtifactCoordinate;

/**
 * An identifier of an artifact being validated. It can be either a Maven coordinate or an URL.
 */
public interface ArtifactIdentifier {

    class UrlResource implements ArtifactIdentifier {
        private final URL resourceUrl;

        public UrlResource(URL resourceUrl) {
            this.resourceUrl = resourceUrl;
        }

        public URL getResourceUrl() {
            return resourceUrl;
        }

        @Override
        public String getDescription() {
            return resourceUrl.toExternalForm();
        }
    }

    class MavenResource extends ArtifactCoordinate implements ArtifactIdentifier {

        public MavenResource(String groupId, String artifactId, String extension, String classifier, String version) {
            super(groupId, artifactId, extension, classifier, version);
        }

        public MavenResource(ArtifactCoordinate artifactCoordinate) {
            super(artifactCoordinate.getGroupId(),
                    artifactCoordinate.getArtifactId(),
                    artifactCoordinate.getExtension(),
                    artifactCoordinate.getClassifier(),
                    artifactCoordinate.getVersion());
        }

        @Override
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append(groupId).append(":").append(artifactId).append(":");
            if (classifier != null && !classifier.isEmpty()) {
                sb.append(classifier).append(":");
            }
            if (extension != null && !extension.isEmpty()) {
                sb.append(extension).append(":");
            }
            sb.append(version);
            return sb.toString();
        }
    }

    String getDescription();

    default boolean isMavenResource() {
        return this instanceof MavenResource;
    }
}
