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

import static java.util.Collections.emptySet;
import static org.wildfly.channel.version.VersionMatcherTestCase.getTestMavenRepositoryURI;
import static org.wildfly.channel.version.VersionMatcherTestCase.mavenRepositoryFromYaml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.spi.MavenResolverBuilder;
import org.wildfly.channel.spi.MavenVersionResolver;

public class SimpleResolverBuilder implements MavenResolverBuilder<MavenVersionResolver> {

    MavenRepository localCache;

    SimpleResolverBuilder() throws IOException {
        localCache = mavenRepositoryFromYaml("url: " + getTestMavenRepositoryURI("local-cache").toUri());

    }
    @Override
    public MavenVersionResolver create(List<MavenRepository> mavenRepositories) {
        return new MavenVersionResolver() {

            @Override
            public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier, boolean resolveLocalCache) {
                try {
                    List<SimplisticMavenRepoManager> repoManagers = new ArrayList<>();
                    if (resolveLocalCache) {
                        repoManagers.add(SimplisticMavenRepoManager.getInstance(Paths.get(localCache.getUrl().toURI())));
                    }
                    for (MavenRepository mavenRepository : mavenRepositories) {
                        repoManagers.add(SimplisticMavenRepoManager.getInstance(Paths.get(mavenRepository.getUrl().toURI())));
                    }

                    Set<String> versions = new HashSet<>();
                    for (SimplisticMavenRepoManager repoManager : repoManagers) {
                        versions.addAll(repoManager.getAllVersions(groupId, artifactId));
                    }
                    return versions;
                } catch (URISyntaxException e) {
                    return emptySet();
                }

            }
        };
    }
}
