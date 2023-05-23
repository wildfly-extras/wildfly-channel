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
package org.wildfly.channel;

import java.util.Set;

public abstract class UnresolvedMavenArtifactException extends RuntimeException {

    private final Set<ArtifactCoordinate> unresolvedArtifacts;
    private final Set<Repository> attemptedRepositories;

    public UnresolvedMavenArtifactException(String localizedMessage,
                                            Throwable cause,
                                            Set<ArtifactCoordinate> unresolvedArtifacts,
                                            Set<Repository> attemptedRepositories) {
        super(localizedMessage, cause);
        this.unresolvedArtifacts = unresolvedArtifacts;
        this.attemptedRepositories = attemptedRepositories;
    }

    public UnresolvedMavenArtifactException(String message, Set<ArtifactCoordinate> unresolvedArtifacts, Set<Repository> attemptedRepositories) {
        super(message);
        this.unresolvedArtifacts = unresolvedArtifacts;
        this.attemptedRepositories = attemptedRepositories;
    }

    public Set<ArtifactCoordinate> getUnresolvedArtifacts() {
        return unresolvedArtifacts;
    }

    public Set<Repository> getAttemptedRepositories() {
        return attemptedRepositories;
    }
}
