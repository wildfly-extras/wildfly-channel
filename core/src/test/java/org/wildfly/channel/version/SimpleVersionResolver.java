
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
package org.wildfly.channel.version;

import static org.wildfly.channel.version.VersionResolutionTestCase.getTestMavenRepositoryURI;
import static org.wildfly.channel.version.VersionResolutionTestCase.mavenRepositoryFromYaml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.spi.MavenVersionResolver;

public class SimpleVersionResolver implements MavenVersionResolver {

    MavenRepository localCache;

    SimpleVersionResolver() throws IOException {
        localCache = mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("local-cache").toUri());
    }

    @Override
    public Optional<String> resolve(String groupId, String artifactId, List<MavenRepository> mavenRepositories, boolean resolveLocalCache, VersionComparator versionComparator) {
        if (resolveLocalCache) {
            Optional<String> found = resolve(groupId, artifactId, localCache, versionComparator);
            if (found.isPresent()) {
                return found;
            }
        }
        for (MavenRepository repository : mavenRepositories) {
            Optional<String> found = resolve(groupId, artifactId, repository, versionComparator);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolve(String groupId, String artifactId, MavenRepository mavenRepository, VersionComparator versionComparator) {
        Path mavenRepo;
        try {
            mavenRepo = Paths.get(mavenRepository.getUrl().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        SimplisticMavenRepoManager mavenRepoManager = SimplisticMavenRepoManager.getInstance(mavenRepo);
        List<String> versionsFromRepo = mavenRepoManager.getAllVersions(groupId, artifactId);
        return versionComparator.matches(versionsFromRepo);
    }
}
