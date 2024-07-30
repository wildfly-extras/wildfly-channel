/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
     * @param signature - local file containing armour encoded detached GPG signature for the {@code artifact}.
     * @param gpgUrls   - URLs of the keys defined in the channel. Empty collection if channel does not define any signatures.
     * @return {@link SignatureResult} with the result of validation
     * @throws SignatureException - if an unexpected error occurred when handling the keys.
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
