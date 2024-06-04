package org.wildfly.channel.spi;

import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.MavenArtifact;

import java.io.File;

public interface SignatureValidator {
    SignatureValidator REJECTING_VALIDATOR = (artifact, signature, gpgUrl) -> {
        throw new SignatureException("Not implemented");
    };

    void validateSignature(MavenArtifact artifact, File signature, String gpgUrl);

    class SignatureException extends RuntimeException {

        private ArtifactCoordinate artifact;
        private String keyId;

        public SignatureException(String message) {
            super(message);
        }

        public SignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
