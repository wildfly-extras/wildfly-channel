package org.wildfly.channel.spi;

import java.io.File;

public interface SignatureValidator {
    SignatureValidator REJECTING_VALIDATOR = (artifact, signature, gpgUrl) -> {
        throw new SignatureException("Not implemented");
    };

    void validateSignature(File artifact, File signature, String gpgUrl);

    class SignatureException extends RuntimeException {
        public SignatureException(String message) {
            super(message);
        }

        public SignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
