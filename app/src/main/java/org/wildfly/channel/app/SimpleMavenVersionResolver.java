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
package org.wildfly.channel.app;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.spi.MavenVersionResolver;

public class SimpleMavenVersionResolver implements MavenVersionResolver {
    private static String LOCAL_MAVEN_REPO = System.getProperty("user.home") + "/.m2/repository";
    private final RepositorySystem system;

    private List<MavenRepository> mavenRepositories;
    private final List<RemoteRepository> remoteRepositories;

    SimpleMavenVersionResolver(List<MavenRepository> mavenRepositories) {
        Objects.requireNonNull(mavenRepositories);
        this.mavenRepositories = new ArrayList<>(mavenRepositories);
        remoteRepositories = mavenRepositories.stream().map(r -> newRemoteRepository(r)).collect(Collectors.toList());
        system = newRepositorySystem();
    }

    @Override
    public List<MavenRepository> getMavenRepositories() {
        return mavenRepositories;
    }

    @Override
    public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier, boolean resolveLocalCache) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        System.out.println(String.format("Resolving the latest version of %s:%s in repositories: %s",
                groupId, artifactId, remoteRepositories.stream().map(r -> r.getUrl()).collect(Collectors.joining(","))));

        RepositorySystemSession session = newRepositorySystemSession(system, resolveLocalCache);

        Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, "[0,)");
        VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
        versionRangeRequest.setArtifact(artifact);
        versionRangeRequest.setRepositories(remoteRepositories);

        try {
            VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
            Set<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toSet());
            System.out.println("All versions in the repositories: " + versions);
            return versions;
        } catch (VersionRangeResolutionException e) {
            return emptySet();
        }
    }

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                exception.printStackTrace();
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, boolean resolveLocalCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String location;
        if (resolveLocalCache) {
            location = LOCAL_MAVEN_REPO;
        } else {
            location = "target/local-repo" ;
        }
        LocalRepository localRepo = new LocalRepository(location);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    private static RemoteRepository newRemoteRepository(MavenRepository mavenRepository) {
        return new RemoteRepository.Builder(mavenRepository.getId(), "default", mavenRepository.getUrl().toExternalForm()).build();
    }
}
