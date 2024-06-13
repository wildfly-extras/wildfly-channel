package org.wildfly.channel.spi;

import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.MavenArtifact;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface SignatureValidator {
    SignatureValidator REJECTING_VALIDATOR = (artifact, signature, gpgUrl) -> {
        throw new SignatureException("Not implemented");
    };

    SignatureResult validateSignature(MavenArtifact artifact, File signature, List<String> gpgUrls) throws IOException, SignatureException;

    class SignatureException extends RuntimeException {

        private ArtifactCoordinate artifact;
        private String keyId;
        private SignatureResult signatureResult;

        public SignatureException(String message) {
            super(message);
        }

        public SignatureException(String message, Throwable cause) {
            super(message, cause);
        }

        public SignatureException(String message, SignatureResult signatureResult) {
            super(message);
            this.signatureResult = signatureResult;
        }

        public SignatureResult getSignatureResult() {
            return signatureResult;
        }
    }
}
