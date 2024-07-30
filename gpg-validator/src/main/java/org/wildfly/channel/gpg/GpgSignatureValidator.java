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
package org.wildfly.channel.gpg;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLConnection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.jboss.logging.Logger;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;

public class GpgSignatureValidator implements SignatureValidator {
    private static final Logger LOG = Logger.getLogger(GpgSignatureValidator.class);
    private final GpgKeystore keystore;
    private final Keyserver keyserver;

    private SignatureValidatorListener listener = new SignatureValidatorListener() {

        @Override
        public void artifactSignatureCorrect(MavenArtifact artifact, PGPPublicKey publicKey) {
            // noop
        }

        @Override
        public void artifactSignatureInvalid(MavenArtifact artifact, PGPPublicKey publicKey) {
            // noop
        }
    };

    public GpgSignatureValidator(GpgKeystore keystore) {
        this(keystore, new Keyserver(Collections.emptyList()));
    }

    public GpgSignatureValidator(GpgKeystore keystore, Keyserver keyserver) {
        this.keystore = keystore;
        this.keyserver = keyserver;
    }

    public void addListener(SignatureValidatorListener listener) {
        this.listener = listener;
    }

    @Override
    public SignatureResult validateSignature(MavenArtifact artifact, File signature, List<String> gpgUrls) throws SignatureException {
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(signature);

        final PGPSignature pgpSignature;
        try {
            pgpSignature = readSignatureFile(signature);
        } catch (IOException e) {
            throw new SignatureException("Could not find signature in provided signature file", e, SignatureResult.noSignature(toArtifactCoordinate(artifact)));
        }

        if (pgpSignature == null) {
            LOG.error("Could not read the signature in provided signature file");
            return SignatureResult.noSignature(toArtifactCoordinate(artifact));
        }

        final String keyID = Long.toHexString(pgpSignature.getKeyID()).toUpperCase(Locale.ROOT);

        final PGPPublicKey publicKey;
        if (keystore.get(keyID) != null) {
            publicKey = keystore.get(keyID);
        } else {
            List<PGPPublicKey> pgpPublicKeys = null;
            PGPPublicKey key = null;
            try {
                final PGPPublicKeyRing keyRing = keyserver.downloadKey(keyID);
                if (keyRing != null) {
                    final Iterator<PGPPublicKey> publicKeys = keyRing.getPublicKeys();
                    key = keyRing.getPublicKey(new BigInteger(keyID, 16).longValue());
                    pgpPublicKeys = new ArrayList<>();
                    while (publicKeys.hasNext()) {
                        pgpPublicKeys.add(publicKeys.next());
                    }
                }
            } catch (PGPException | IOException e) {
                throw new SignatureException("Unable to parse the certificate downloaded from keyserver", e, SignatureResult.noSignature(artifact));
            }

            if (key == null) {
                for (String gpgUrl : gpgUrls) {
                    try {
                        pgpPublicKeys = downloadPublicKey(gpgUrl);
                    } catch (IOException e) {
                        throw new SignatureException("Unable to parse the certificate downloaded from " + gpgUrl, e,
                                SignatureResult.noSignature(artifact));
                    }
                    if (pgpPublicKeys.stream().anyMatch(k -> k.getKeyID() == pgpSignature.getKeyID())) {
                        key = pgpPublicKeys.stream().filter(k -> k.getKeyID() == pgpSignature.getKeyID()).findFirst().get();
                        break;
                    }
                }
            }
            if (key == null) {
                final ArtifactCoordinate coord = toArtifactCoordinate(artifact);
                return SignatureResult.noMatchingCertificate(coord, keyID);
            } else {
                if (keystore.add(pgpPublicKeys)) {
                    publicKey = key;
                } else {
                    final ArtifactCoordinate coord = toArtifactCoordinate(artifact);
                    return SignatureResult.noMatchingCertificate(coord, keyID);
                }
            }
        }

        final Iterator<PGPSignature> subKeys = publicKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
        while (subKeys.hasNext()) {
            final PGPSignature subKey = subKeys.next();
            final PGPPublicKey masterKey = keystore.get(Long.toHexString(subKey.getKeyID()).toUpperCase(Locale.ROOT));
            if (masterKey.hasRevocation()) {
                return SignatureResult.revoked(toArtifactCoordinate(artifact), keyID, getRevocationReason(publicKey));
            }
        }

        if (publicKey.hasRevocation()) {
            return SignatureResult.revoked(toArtifactCoordinate(artifact), keyID, getRevocationReason(publicKey));
        }

        if (publicKey.getValidSeconds() > 0) {
            final Instant expiry = Instant.from(publicKey.getCreationTime().toInstant().plus(publicKey.getValidSeconds(), ChronoUnit.SECONDS));
            if (expiry.isBefore(Instant.now())) {
                return SignatureResult.expired(toArtifactCoordinate(artifact), keyID);
            }
        }

        try {
            pgpSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);
        } catch (PGPException e) {
            throw new SignatureException("Unable to verify the signature using key " + keyID, e,
                    SignatureResult.invalid(artifact));
        }

        final SignatureResult result = verifyFile(artifact, pgpSignature);

        if (result.getResult() == SignatureResult.Result.OK) {
            listener.artifactSignatureCorrect(artifact, publicKey);
        } else {
            listener.artifactSignatureInvalid(artifact, publicKey);
        }

        return result;
    }

    private static ArtifactCoordinate toArtifactCoordinate(MavenArtifact artifact) {
        final ArtifactCoordinate coord = new ArtifactCoordinate(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
        return coord;
    }

    private static String getRevocationReason(PGPPublicKey publicKey) {
        Iterator<PGPSignature> keySignatures = publicKey.getSignaturesOfType(PGPSignature.KEY_REVOCATION);
        String revocationDescription = null;
        while (keySignatures.hasNext()) {
            final PGPSignature sign = keySignatures.next();
            if (sign.getSignatureType() == PGPSignature.KEY_REVOCATION) {
                final PGPSignatureSubpacketVector hashedSubPackets = sign.getHashedSubPackets();
                revocationDescription = hashedSubPackets.getRevocationReason().getRevocationDescription();
            }
        }
        return revocationDescription;
    }

    private static SignatureResult verifyFile(MavenArtifact mavenArtifact, PGPSignature pgpSignature) throws SignatureException {
        // Read file to verify
        byte[] data = new byte[1024];
        InputStream inputStream = null;
        try {
            inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(mavenArtifact.getFile())));
            while (true) {
                int bytesRead = inputStream.read(data, 0, 1024);
                if (bytesRead == -1)
                    break;
                pgpSignature.update(data, 0, bytesRead);
            }
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Verify the signature
        try {
            if (!pgpSignature.verify()) {
                return SignatureResult.invalid(mavenArtifact);
            } else {
                return SignatureResult.ok();
            }
        } catch (PGPException e) {
            throw new SignatureException("Unable to verify the file signature", e, SignatureResult.invalid(mavenArtifact));
        }
    }

    private static PGPSignature readSignatureFile(File signatureFile) throws IOException {
        PGPSignature pgpSignature = null;
        try (InputStream decoderStream = PGPUtil.getDecoderStream(new FileInputStream(signatureFile))) {
            PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(decoderStream, new JcaKeyFingerprintCalculator());
            Object o = pgpObjectFactory.nextObject();
            if (o instanceof PGPSignatureList) {
                PGPSignatureList signatureList = (PGPSignatureList) o;
                if (signatureList.isEmpty()) {
                    throw new RuntimeException("signatureList must not be empty");
                }
                pgpSignature = signatureList.get(0);
            } else if (o instanceof PGPSignature) {
                pgpSignature = (PGPSignature) o;
            }
        }
        return pgpSignature;
    }

    private static List<PGPPublicKey> downloadPublicKey(String signatureUrl) throws IOException {
        final URI uri = URI.create(signatureUrl);
        final InputStream inputStream;
        if (uri.getScheme().equals("classpath")) {
            final String keyPath = uri.getSchemeSpecificPart();
            inputStream = GpgSignatureValidator.class.getClassLoader().getResourceAsStream(keyPath);
        } else {
            final URLConnection urlConnection = uri.toURL().openConnection();
            urlConnection.connect();
            inputStream = urlConnection.getInputStream();
        }
        try (InputStream decoderStream = new ArmoredInputStream(inputStream)) {
            final PGPPublicKeyRing pgpPublicKeys = new PGPPublicKeyRing(decoderStream, new JcaKeyFingerprintCalculator());
            final ArrayList<PGPPublicKey> res = new ArrayList<>();
            final Iterator<PGPPublicKey> publicKeys = pgpPublicKeys.getPublicKeys();
            while (publicKeys.hasNext()) {
                res.add(publicKeys.next());
            }
            return res;
        }
    }

    public interface SignatureValidatorListener {

        void artifactSignatureCorrect(MavenArtifact artifact, PGPPublicKey publicKey);

        void artifactSignatureInvalid(MavenArtifact artifact, PGPPublicKey publicKey);
    }
}
