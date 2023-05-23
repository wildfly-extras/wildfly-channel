package org.wildfly.channel;

import java.util.Set;

/**
 * Thrown in case of an error during downloading of one or more of required artifacts.
 */
public class ArtifactTransferException extends UnresolvedMavenArtifactException {

    public ArtifactTransferException(String localizedMessage,
                                    Throwable cause,
                                    Set<ArtifactCoordinate> unresolvedArtifacts,
                                    Set<Repository> attemptedRepositories) {
        super(localizedMessage, cause, unresolvedArtifacts, attemptedRepositories);
    }

    public ArtifactTransferException(String message, Set<ArtifactCoordinate> unresolvedArtifacts, Set<Repository> attemptedRepositories) {
        super(message, unresolvedArtifacts, attemptedRepositories);
    }
}
