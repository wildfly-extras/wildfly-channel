
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

import static java.util.Arrays.asList;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.Stream;

public class SimpleVersionResolver implements StreamVersionResolver{

    VersionComparator comparator = new VersionComparator();

    public Optional<String> resolveVersion(Stream stream, List<MavenRepository> mavenRepository) {
        for (MavenRepository repository : mavenRepository) {
            Optional<String> found = resolveVersion(stream, repository);
            if (found.isPresent()) {
                return found;
            }
        }
        if (stream.isResolveWithLocalCache()) {
            Optional<String> found = resolveVersionFromLocalCache(stream);
            return found;
        }
        return Optional.empty();
    }

    private Optional<String> resolveVersionFromLocalCache(Stream stream) {
        return Optional.empty();
    }

    private Optional<String> resolveVersion(Stream stream, MavenRepository mavenRepository)  {
        Path mavenRepo;
        try {
            mavenRepo = Paths.get(mavenRepository.getUrl().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        SimplisticMavenRepoManager mavenRepoManager = SimplisticMavenRepoManager.getInstance(mavenRepo);
        List<String> versionsFromRepo = mavenRepoManager.getAllVersions(stream.getGroupId(), stream.getArtifactId());

        List<String> versionsFromStream = asList(stream.getVersion().split("[\\s,]+"));
        return comparator.matches(versionsFromStream, versionsFromRepo);
    }
}
