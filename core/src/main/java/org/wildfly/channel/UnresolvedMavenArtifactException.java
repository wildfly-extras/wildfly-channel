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

import java.util.Collections;
import java.util.Set;

public class UnresolvedMavenArtifactException extends RuntimeException {

    private Set<ArtifactCoordinate> unresolvedArtifacts = Collections.emptySet();

    public UnresolvedMavenArtifactException(String message) {
        super(message);
    }

    public UnresolvedMavenArtifactException() {
        super();
    }

    public UnresolvedMavenArtifactException(String localizedMessage,
                                            Throwable cause,
                                            Set<ArtifactCoordinate> unresolvedArtifacts) {
        super(localizedMessage, cause);
        this.unresolvedArtifacts = unresolvedArtifacts;
    }

    public Set<ArtifactCoordinate> getUnresolvedArtifacts() {
        return unresolvedArtifacts;
    }
}
