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

/**
 * Represents a result of artifact verification
 */
public class SignatureResult {

    /**
     * Identifier of the artifact that was being verified.
     */
    private ArtifactIdentifier resource;
    /**
     * Identifier of the certificate used to verify the artifact.
     */
    private String keyId;
    /**
     * Optional message with details of validation.
     */
    private String message;

    public static SignatureResult noMatchingCertificate(ArtifactIdentifier resource, String keyID) {
        return new SignatureResult(Result.NO_MATCHING_CERT, resource, keyID, null);
    }

    public static SignatureResult revoked(ArtifactIdentifier resource, String keyID, String revocationReason) {
        return new SignatureResult(Result.REVOKED, resource, keyID, revocationReason);
    }

    public static SignatureResult expired(ArtifactIdentifier resource, String keyID) {
        return new SignatureResult(Result.EXPIRED, resource, keyID, null);
    }

    public static SignatureResult noSignature(ArtifactIdentifier resource) {
        return new SignatureResult(Result.NO_SIGNATURE, resource, null, null);
    }

    public static SignatureResult invalid(ArtifactIdentifier resource, String keyID) {
        return new SignatureResult(Result.INVALID, resource, keyID, null);
    }

    public enum Result {OK, NO_MATCHING_CERT, REVOKED, EXPIRED, NO_SIGNATURE, INVALID;}
    private final Result result;
    public static SignatureResult ok() {
        return new SignatureResult(Result.OK, null, null, null);
    }

    private SignatureResult(Result result, ArtifactIdentifier resource, String keyID, String message) {
        this.result = result;
        this.resource = resource;
        this.keyId = keyID;
        this.message = message;
    }

    public Result getResult() {
        return result;
    }

    public ArtifactIdentifier getResource() {
        return resource;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getMessage() {
        return message;
    }
}
