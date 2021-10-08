/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.channel.version;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Alexey Loubyansky
 */
public class SimplisticMavenRepoManager {

    public static SimplisticMavenRepoManager getInstance(Path repoHome) {
        return new SimplisticMavenRepoManager(repoHome);
    }

    protected final Path repoHome;

    private SimplisticMavenRepoManager(Path repoHome) {
        this.repoHome = repoHome;
    }

    private Path getArtifactIdPath(String groupId, String artifactId)  {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        Path p = repoHome;
        final String[] groupParts = groupId.split("\\.");
        for (String part : groupParts) {
            p = p.resolve(part);
        }
        return p.resolve(artifactId);
    }

    public List<String> getAllVersions(String groupId, String artifactId) {
        Path artifactDir = getArtifactIdPath(groupId, artifactId);
        try {
            List<String> versions =
                    Files.list(artifactDir)
                            .filter(Files::isDirectory)
                            .map(p -> p.toFile().getName())
                            // exclude dir staring with non-numerical character
                            .filter(name -> Character.isDigit(name.charAt(0)))
                            .collect(Collectors.toList());
            return versions;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
