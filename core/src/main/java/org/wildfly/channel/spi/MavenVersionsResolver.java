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
package org.wildfly.channel.spi;

import java.io.Closeable;
import java.io.File;
import java.util.Set;

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

   File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException;

   default void close() {
   }


   /**
    * Factory API to build MavenVersionResolver.
    *
    * A client of this library is responsible to provide an implementation of the {@link Factory} interface.
    *
    * The {@link #create()} method will be called once for every channel that will be checked for the latest version
    * of a given Maven artifact.
    */
   interface Factory extends Closeable {

      MavenVersionsResolver create();

      default void close() {
      }
   }
}
