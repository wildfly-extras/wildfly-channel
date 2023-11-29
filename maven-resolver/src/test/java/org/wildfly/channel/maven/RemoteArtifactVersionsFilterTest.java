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

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteArtifactVersionsFilterTest {

    @TempDir
    private Path tempDir;

    @Test
    public void rejectIfNoRepositoriesAreUsed() {
        final RepositorySystemSession session = mock(RepositorySystemSession.class);;
        final VersionRangeResult result = new VersionRangeResult(mock(VersionRangeRequest.class));

        final RemoteArtifactVersionsFilter filter = new RemoteArtifactVersionsFilter(session, result);
        Assertions.assertFalse(filter.accept(mock(Version.class)), "A version requests without any remote repository should be rejected");
    }

    @Test
    public void acceptIfTheVersionWasFoundInRemoteRepository() {
        final RepositorySystemSession session = mock(RepositorySystemSession.class);;
        final RemoteRepository repository = new RemoteRepository.Builder("test", "default", "http://foo.bar").build();
        final DefaultArtifact artifact = new DefaultArtifact("org.test", "test-one", "jar", "1.1.1");
        final VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest(artifact, List.of(repository), null));
        result.setRepository(new TestVersion("1.1.1"), repository);

        final RemoteArtifactVersionsFilter filter = new RemoteArtifactVersionsFilter(session, result);
        Assertions.assertTrue(filter.accept(new TestVersion("1.1.1")), "A version requests resolved from a remote repository should be accepted");
    }

    @Test
    public void acceptIfTheVersionIsInRemoteCache() throws IOException {
        final RepositorySystemSession session = mock(RepositorySystemSession.class);;
        final LocalRepositoryManager lrm = mock(LocalRepositoryManager.class);
        final LocalRepository lr = mock(LocalRepository.class);
        final RemoteRepository repository = new RemoteRepository.Builder("test", "default", "http://foo.bar").build();
        final LocalRepository localRepository = new LocalRepository(tempDir.toFile());
        final String groupId = "org.test";
        final String artifactId = "test-one";
        final String version = "1.1.1";
        final DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        final VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest(artifact, List.of(repository), null));
        result.setRepository(new TestVersion(version), localRepository);

        when(session.getLocalRepositoryManager()).thenReturn(lrm);
        when(lrm.getPathForRemoteMetadata(any(), any(), any())).thenReturn(groupId.replace('.', File.separatorChar) +
                File.separatorChar + artifactId + File.separatorChar + "maven-metadata-test.xml");
        when(session.getLocalRepository()).thenReturn(lr);
        when(lr.getBasedir()).thenReturn(tempDir.toFile());

        writeMavenCacheFile(groupId, artifactId, version);

        final RemoteArtifactVersionsFilter filter = new RemoteArtifactVersionsFilter(session, result);
        Assertions.assertTrue(filter.accept(new TestVersion(version)), "A version requests with a matching version in cache file should be accepted");
    }

    @Test
    public void rejectIfTheVersionIsNotInRemoteCache() throws IOException {
        final RepositorySystemSession session = mock(RepositorySystemSession.class);;
        final LocalRepositoryManager lrm = mock(LocalRepositoryManager.class);
        final LocalRepository lr = mock(LocalRepository.class);
        final RemoteRepository repository = new RemoteRepository.Builder("test", "default", "http://foo.bar").build();
        final LocalRepository localRepository = new LocalRepository(tempDir.toFile());
        final String groupId = "org.test";
        final String artifactId = "test-one";
        final String version = "1.1.1";
        final DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        final VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest(artifact, List.of(repository), null));
        result.setRepository(new TestVersion(version), localRepository);

        when(session.getLocalRepositoryManager()).thenReturn(lrm);
        when(lrm.getPathForRemoteMetadata(any(), any(), any())).thenReturn(groupId.replace('.', File.separatorChar) +
                File.separatorChar + artifactId + File.separatorChar + "maven-metadata-test.xml");
        when(session.getLocalRepository()).thenReturn(lr);
        when(lr.getBasedir()).thenReturn(tempDir.toFile());

        writeMavenCacheFile(groupId, artifactId, "1.1.2");

        final RemoteArtifactVersionsFilter filter = new RemoteArtifactVersionsFilter(session, result);
        Assertions.assertFalse(filter.accept(new TestVersion(version)), "A version requests without matching version in cache should be rejected");
    }

    private void writeMavenCacheFile(String groupId, String artifactId, String version) throws IOException {
        final Metadata metadata = new Metadata();
        final Versioning versioning = new Versioning();
        versioning.setVersions(List.of(version));
        metadata.setGroupId(groupId);
        metadata.setArtifactId(artifactId);
        metadata.setVersioning(versioning);
        final Path manifestFile = tempDir.resolve(Path.of(groupId.replace('.', File.separatorChar), artifactId, "maven-metadata-test.xml"));
        Files.createDirectories(manifestFile.getParent());
        new MetadataXpp3Writer().write(new FileWriter(manifestFile.toFile()), metadata);
    }


    static class TestVersion implements Version {

        private final String version;

        TestVersion(String version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return this.version;
        }

        @Override
        public int compareTo(Version v) {
            TestVersion other = (TestVersion) v;
            return this.version.compareTo(other.version);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestVersion that = (TestVersion) o;
            return Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version);
        }
    }
}