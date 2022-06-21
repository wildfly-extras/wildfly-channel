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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class DefaultArtifactCoordinate implements ArtifactCoordinate {

   protected final String groupId;
   protected final String artifactId;
   protected final String extension;
   protected final String classifier;
   protected final String version;

   public DefaultArtifactCoordinate(String groupId, String artifactId, String extension, String classifier, String version) {
      requireNonNull(groupId);
      requireNonNull(artifactId);

      this.groupId = groupId;
      this.artifactId = artifactId;
      this.extension = extension;
      this.classifier = classifier;
      this.version = version;
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

   @Override
   public String toString() {
      return "MavenCoordinate{" +
         "groupId='" + groupId + '\'' +
         ", artifactId='" + artifactId + '\'' +
         ", extension='" + extension + '\'' +
         ", classifier='" + classifier + '\'' +
         ", version='" + version + '\'' +
         '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      DefaultArtifactCoordinate that = (DefaultArtifactCoordinate) o;
      return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(extension, that.extension) && Objects.equals(classifier, that.classifier) && Objects.equals(version, that.version);
   }

   @Override
   public int hashCode() {
      return Objects.hash(groupId, artifactId, extension, classifier, version);
   }
}
