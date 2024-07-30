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

import org.wildfly.channel.ArtifactCoordinate;

public class SignatureResult {

    private ArtifactCoordinate coord;
    private String keyId;
    private String message;

    public static SignatureResult noMatchingCertificate(ArtifactCoordinate coord, String keyID) {
        return new SignatureResult(Result.NO_MATCHING_CERT, coord, keyID, null);
    }

    public static SignatureResult revoked(ArtifactCoordinate artifactCoordinate, String keyID, String revocationReason) {
        return new SignatureResult(Result.REVOKED, artifactCoordinate, keyID, revocationReason);
    }

    public static SignatureResult expired(ArtifactCoordinate artifactCoordinate, String keyID) {
        return new SignatureResult(Result.EXPIRED, artifactCoordinate, keyID, null);
    }

    public static SignatureResult noSignature(ArtifactCoordinate artifactCoordinate) {
        return new SignatureResult(Result.NO_SIGNATURE, artifactCoordinate, null, null);
    }

    public static SignatureResult invalid(ArtifactCoordinate artifactCoordinate) {
        return new SignatureResult(Result.INVALID, artifactCoordinate, null, null);
    }

    public enum Result {OK, NO_MATCHING_CERT, REVOKED, EXPIRED, NO_SIGNATURE, INVALID;}
    private final Result result;
    public static SignatureResult ok() {
        return new SignatureResult(Result.OK, null, null, null);
    }

    private SignatureResult(Result result, ArtifactCoordinate coord, String keyID, String message) {
        this.result = result;
        this.coord = coord;
        this.keyId = keyID;
        this.message = message;
    }

    public Result getResult() {
        return result;
    }

    public ArtifactCoordinate getArtifact() {
        return coord;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getMessage() {
        return message;
    }
}
