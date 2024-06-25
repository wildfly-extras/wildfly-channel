package org.wildfly.channel.spi;

import org.wildfly.channel.MavenArtifact;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Called to validate detached signatures of artifacts resolved in the channel
 */
public interface SignatureValidator {
    SignatureValidator REJECTING_VALIDATOR = (artifact, signature, gpgUrl) -> {
        throw new SignatureException("Not implemented", SignatureResult.noSignature(artifact));
    };

    /**
     * validates a signature of {@code artifact}. The locally downloaded {@code signature} has to be an armour encoded GPG signature.
     *
     * @param artifact  - {@code MavenArtifact} to validate. Includes a full GAV and the local artifact file.
     * @param signature - armour encoded detached GPG signature file.
     * @param gpgUrls   - URLs of the keys defined in the channel.
     * @return
     * @throws IOException
     * @throws SignatureException
     */
    SignatureResult validateSignature(MavenArtifact artifact, File signature, List<String> gpgUrls) throws SignatureException;

    class SignatureException extends RuntimeException {
        private final SignatureResult signatureResult;

        public SignatureException(String message, Throwable cause, SignatureResult signatureResult) {
            super(message, cause);
            this.signatureResult = signatureResult;
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
