package org.wildfly.channel;

import java.util.Set;

/**
 * Thrown if one or more of required artifacts are not found in specified Channels
 */
public class NoStreamFoundException extends UnresolvedMavenArtifactException {

    public NoStreamFoundException(String localizedMessage,
                                  Throwable cause,
                                  Set<ArtifactCoordinate> unresolvedArtifacts,
                                  Set<Repository> attemptedRepositories) {
        super(localizedMessage, cause, unresolvedArtifacts, attemptedRepositories);
    }

    public NoStreamFoundException(String message, Set<ArtifactCoordinate> unresolvedArtifacts, Set<Repository> attemptedRepositories) {
        super(message, unresolvedArtifacts, attemptedRepositories);
    }
}
