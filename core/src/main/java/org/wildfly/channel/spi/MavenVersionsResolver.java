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
package org.wildfly.channel.spi;

import java.io.Closeable;
import java.io.File;
import java.util.List;
import java.util.Set;

import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.UnresolvedMavenArtifactException;

/**
 * SPI Interface implemented by tooling doing the Maven request to get all the versions for the given artifact.
 *
 * A client of this library is responsible for implementation {@link MavenVersionsResolver} to query Maven for all the versions for a given Artifact.
 */
public interface MavenVersionsResolver extends Closeable {
   /**
    * Returns all the versions provided by Maven for the given artifact.
    *
    * @param groupId Maven GroupId - required
    * @param artifactId Maven ArtifactId - required
    * @param extension Maven extension - can be {@code null}
    * @param classifier Maven classifier - can be {@code null}
    *
    * @return the set of versions.
    */
   Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier);

   /**
    * Resolve the maven artifact based on the full coordinates.
    *
    * @param groupId Maven GroupId - required
    * @param artifactId Maven ArtifactId - required
    * @param extension Maven extension - can be {@code null}
    * @param classifier Maven classifier - can be {@code null}
    * @param version Maven version - required
    *
    * @return a File representing the resolved Maven artifact.
    *
    * @throws UnresolvedMavenArtifactException if the artifact can not be resolved.
    */
   File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException;

   /**
    * Resolve a list of maven artifacts based on the full coordinates.
    *
    * The order of returned Files is the same as order of coordinates.
    *
    * @param coordinates - list of ArtifactCoordinates. They need contain at least groupId, artifactId and version.
    *
    * @return a list of File representing the resolved Maven artifact.
    *
    * @throws UnresolvedMavenArtifactException if any artifacts can not be resolved.
    */
   List<File> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException;

   default void close() {
   }


   /**
    * Factory API to build MavenVersionResolver.
    *
    * A client of this library is responsible to provide an implementation of the {@link Factory} interface.
    *
    * The {@link #create()} method will be called once for every channel.
    */
   interface Factory extends Closeable {

      MavenVersionsResolver create();

      default void close() {
      }
   }
}
