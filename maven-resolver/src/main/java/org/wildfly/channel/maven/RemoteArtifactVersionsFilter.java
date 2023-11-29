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

package org.wildfly.channel.maven;

import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class RemoteArtifactVersionsFilter {

    private static final Logger LOG = Logger.getLogger(RemoteArtifactVersionsFilter.class);

    private final RepositorySystemSession session;
    private final Artifact artifact;
    private final List<RemoteRepository> repositories;
    private final Set<String> remoteRepositoryIds;
    private final VersionRangeResult versionRangeResult;

    RemoteArtifactVersionsFilter(RepositorySystemSession session, VersionRangeResult versionRangeResult) {
        this.session = session;
        this.artifact = versionRangeResult.getRequest().getArtifact();
        this.versionRangeResult = versionRangeResult;
        this.repositories = versionRangeResult.getRequest().getRepositories();
        this.remoteRepositoryIds = repositories.stream().map(RemoteRepository::getId).collect(Collectors.toSet());
    }

    /**
     * rejects versions available only in the local repository
     *
     * @param version
     * @return
     */
    boolean accept(Version version) {
        if (repositories.isEmpty()) {
            return false;
        }

        // if the version was resolved from a remote repository, accept this version
        if (versionRangeResult.getRepository(version) != null && remoteRepositoryIds.contains(versionRangeResult.getRepository(version).getId())) {
            return true;
        }

        /*
         * If the version was resolved from a local repository, it might be because the local version masks the
         * remote repository (see DefaultVersionRangeResolver#getVersions).
         *
         * During the resolution, the versions from remote repositories are cached in maven-metadata-*.xml.
         * We are checking those to see if any of the remote repositories contain the same version as local.
         */
        for (RemoteRepository repository : repositories) {
            final DefaultMetadata artifactMetadata = new DefaultMetadata(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    "maven-metadata.xml",
                    Metadata.Nature.RELEASE);
            final String pathForRemoteMetadata = session.getLocalRepositoryManager().getPathForRemoteMetadata(artifactMetadata, repository, null);
            final File metadataFile = new File(session.getLocalRepository().getBasedir(), pathForRemoteMetadata);

            if (metadataFile.exists()) {
                try {
                    final org.apache.maven.artifact.repository.metadata.Metadata metadata = new MetadataXpp3Reader().read(new FileReader(metadataFile));
                    if (metadata.getVersioning().getVersions().contains(version.toString())) {
                        return true;
                    }
                } catch (IOException | XmlPullParserException e) {
                    LOG.warn("Failed to parse version information in " + metadataFile + ", skipping.", e);
                }
            }
        }
        return false;
    }
}
