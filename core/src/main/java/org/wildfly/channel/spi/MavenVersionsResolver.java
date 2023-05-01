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
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.wildfly.channel.ArtifactChecker;

import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.Repository;
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

   /**
    * Resolve a list of channel metadata artifacts based on the coordinates.
    * If the {@code ChannelMetadataCoordinate} contains non-null URL, that URL is returned.
    * If the {@code ChannelMetadataCoordinate} contains non-null Maven coordinates, the Maven artifact will be resolved
    * and a URL to it will be returned.
    * If the Maven coordinates specify only groupId and artifactId, latest available version of matching Maven artifact
    * will be resolved.
    *
    * The order of returned URLs is the same as order of coordinates.
    *
    * @param manifestCoords - list of ChannelMetadataCoordinate.
    * @param checker The artifact checker.
    *
    * @return a list of URLs to the metadata files
    *
    * @throws UnresolvedMavenArtifactException if any artifacts can not be resolved.
    */
   List<URL> resolveChannelMetadata(List<? extends ChannelMetadataCoordinate> manifestCoords, ArtifactChecker checker) throws UnresolvedMavenArtifactException;

   /**
    * Returns the {@code <release>} version according to the repositories' Maven metadata. If multiple repositories
    * contain the same artifact, {@link org.wildfly.channel.version.VersionMatcher#COMPARATOR} is used to choose version.
    *
    * @param groupId Maven GroupId - required
    * @param artifactId Maven ArtifactId - required
    *
    * @return the {@code release} version.
    * @throws UnresolvedMavenArtifactException if the metadata can not be resolved or is incomplete.
    */
   String getMetadataReleaseVersion(String groupId, String artifactId);

   /**
    * Returns the {@code <latest>} version according to the repositories' Maven metadata. If multiple repositories
    * contain the same artifact, {@link org.wildfly.channel.version.VersionMatcher#COMPARATOR} is used to choose version.
    *
    * @param groupId Maven GroupId - required
    * @param artifactId Maven ArtifactId - required
    *
    * @return the {@code latest} version.
    * @throws UnresolvedMavenArtifactException if the metadata can not be resolved or is incomplete.
    */
   String getMetadataLatestVersion(String groupId, String artifactId);

   default void close() {
   }


   /**
    * Factory API to build MavenVersionResolver.
    *
    * A client of this library is responsible to provide an implementation of the {@link Factory} interface.
    *
    * The {@link #create(Collection)}} method will be called once for every channel.
    */
   interface Factory extends Closeable {

      MavenVersionsResolver create(Collection<Repository> repositories);

      default void close() {
      }
   }
}
