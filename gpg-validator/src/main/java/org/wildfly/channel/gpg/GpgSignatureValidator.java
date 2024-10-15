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
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.channel.spi.ArtifactIdentifier;

/**
 * Implementation of a GPG signature validator.
 *
 * Uses a combination of a local {@link GpgKeystore} and {@code GPG keyservers} to resolve certificates.
 * To resolve a public key required by the artifact signature:
 * <ul>
 *     <li>check if the key is present in the local GpgKeystore.</li>
 *     <li>check if one of the configured remote keystores contains the key.</li>
 *     <li>try to download the keys linked in the {@code gpgUrls}</li>
 * </ul>
 *
 * The {@code GpgKeystore} acts as a source of trusted keys. A new key, resolved from either the keyserver or
 * the gpgUrls is added to the GpgKeystore and used in subsequent checks.
 */
public class GpgSignatureValidator implements SignatureValidator {
    private static final Logger LOG = Logger.getLogger(GpgSignatureValidator.class);
    private final GpgKeystore keystore;
    private final Keyserver keyserver;

    private GpgSignatureValidatorListener listener = new NoopListener();

    public GpgSignatureValidator(GpgKeystore keystore) {
        this(keystore, new Keyserver(Collections.emptyList()));
    }

    public GpgSignatureValidator(GpgKeystore keystore, Keyserver keyserver) {
        this.keystore = keystore;
        this.keyserver = keyserver;
    }

    public void addListener(GpgSignatureValidatorListener listener) {
        this.listener = listener;
    }

    @Override
    public SignatureResult validateSignature(ArtifactIdentifier artifactId, InputStream artifactStream,
                                             InputStream signatureStream, List<String> gpgUrls) throws SignatureException {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(artifactStream);
        Objects.requireNonNull(signatureStream);

        final PGPSignature pgpSignature;
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Reading the signature of artifact.");
            }
            pgpSignature = readSignatureFile(signatureStream);
        } catch (IOException e) {
            throw new SignatureException("Could not find signature in provided signature file", e,
                    SignatureResult.noSignature(artifactId));
        }

        if (pgpSignature == null) {
            LOG.error("Could not read the signature in provided signature file");
            return SignatureResult.noSignature(artifactId);
        }

        final String keyID = getKeyID(pgpSignature);
        if (LOG.isTraceEnabled()) {
            LOG.tracef("The signature was created using public key %s.", keyID);
        }

        final PGPPublicKey publicKey;
        if (keystore.get(keyID) != null) {
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Using a public key %s was found in the local keystore.", keyID);
            }
            publicKey = keystore.get(keyID);
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Trying to download a public key %s from remote keyservers.", keyID);
            }
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
                throw new SignatureException("Unable to parse the certificate downloaded from keyserver", e,
                        SignatureResult.noMatchingCertificate(artifactId, keyID));
            }

            if (key == null) {
                for (String gpgUrl : gpgUrls) {
                    if (LOG.isTraceEnabled()) {
                        LOG.tracef("Trying to download a public key %s from channel defined URL %s.", keyID, gpgUrl);
                    }
                    try {
                        pgpPublicKeys = downloadPublicKey(gpgUrl);
                    } catch (IOException e) {
                        throw new SignatureException("Unable to parse the certificate downloaded from " + gpgUrl, e,
                                SignatureResult.noMatchingCertificate(artifactId, keyID));
                    }
                    if (pgpPublicKeys.stream().anyMatch(k -> k.getKeyID() == pgpSignature.getKeyID())) {
                        key = pgpPublicKeys.stream().filter(k -> k.getKeyID() == pgpSignature.getKeyID()).findFirst().get();
                        break;
                    }
                }

                if (key == null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.tracef("A public key %s not found in the channel defined URLs.", keyID);
                    }
                    return SignatureResult.noMatchingCertificate(artifactId, keyID);
                }
            }


            if (keystore.add(pgpPublicKeys)) {
                if (LOG.isTraceEnabled()) {
                    LOG.tracef("Adding a public key %s to the local keystore.", keyID);
                }
                publicKey = key;
            } else {
                return SignatureResult.noMatchingCertificate(artifactId, keyID);
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Checking if the public key %s is still valid.", artifactId);
        }
        SignatureResult res = checkRevoked(artifactId, keyID, publicKey);
        if (res.getResult() != SignatureResult.Result.OK) {
            return res;
        }

        res = checkExpired(artifactId, publicKey, keyID);
        if (res.getResult() != SignatureResult.Result.OK) {
            return res;
        }

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Verifying that artifact %s has been signed with public key %s.", artifactId, keyID);
        }
        try {
            pgpSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);
        } catch (PGPException e) {
            throw new SignatureException("Unable to verify the signature using key " + keyID, e,
                    SignatureResult.invalid(artifactId, keyID));
        }
        final SignatureResult result = verifyFile(artifactId, artifactStream, pgpSignature);

        if (result.getResult() == SignatureResult.Result.OK) {
            listener.artifactSignatureCorrect(artifactId, publicKey);
        } else {
            listener.artifactSignatureInvalid(artifactId, publicKey);
        }

        return result;
    }

    private static SignatureResult checkExpired(ArtifactIdentifier artifactId, PGPPublicKey publicKey, String keyID) {
        if (LOG.isTraceEnabled()) {
            LOG.tracef("Checking if public key %s is not expired.", keyID);
        }
        if (publicKey.getValidSeconds() > 0) {
            final Instant expiry = Instant.from(publicKey.getCreationTime().toInstant().plus(publicKey.getValidSeconds(), ChronoUnit.SECONDS));
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Public key %s expirates on %s.", keyID, expiry);
            }
            if (expiry.isBefore(Instant.now())) {
                return SignatureResult.expired(artifactId, keyID);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Public key %s has no expiration.", keyID);
            }
        }
        return SignatureResult.ok();
    }

    private SignatureResult checkRevoked(ArtifactIdentifier artifactId, String keyID, PGPPublicKey publicKey) {
        if (LOG.isTraceEnabled()) {
            LOG.tracef("Checking if public key %s has been revoked.", keyID);
        }

        if (publicKey.hasRevocation()) {
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Public key %s has been revoked.", keyID);
            }
            return SignatureResult.revoked(artifactId, keyID, getRevocationReason(publicKey));
        }

        final Iterator<PGPSignature> subKeys = publicKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
        while (subKeys.hasNext()) {
            final PGPSignature subKeySignature = subKeys.next();
            final PGPPublicKey subKey = keystore.get(getKeyID(subKeySignature));
            if (subKey.hasRevocation()) {
                if (LOG.isTraceEnabled()) {
                    LOG.tracef("Sub-key %s has been revoked.", Long.toHexString(subKey.getKeyID()).toUpperCase(Locale.ROOT));
                }
                return SignatureResult.revoked(artifactId, keyID, getRevocationReason(publicKey));
            }
        }
        return SignatureResult.ok();
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

    private static SignatureResult verifyFile(ArtifactIdentifier artifactSource, InputStream artifactStream, PGPSignature pgpSignature) throws SignatureException {
        // Read file to verify
        byte[] data = new byte[1024];
        InputStream inputStream = null;
        try {
            inputStream = new DataInputStream(new BufferedInputStream(artifactStream));
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
                return SignatureResult.invalid(artifactSource, getKeyID(pgpSignature));
            } else {
                return SignatureResult.ok();
            }
        } catch (PGPException e) {
            throw new SignatureException("Unable to verify the file signature", e,
                    SignatureResult.invalid(artifactSource, getKeyID(pgpSignature)));
        }
    }

    private static String getKeyID(PGPSignature pgpSignature) {
        return Long.toHexString(pgpSignature.getKeyID()).toUpperCase(Locale.ROOT);
    }

    private static PGPSignature readSignatureFile(InputStream signatureStream) throws IOException {
        PGPSignature pgpSignature = null;
        try (InputStream decoderStream = PGPUtil.getDecoderStream(signatureStream)) {
            final PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(decoderStream, new JcaKeyFingerprintCalculator());
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
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Resolving the public key from classpath %s.", uri);
            }
            final String keyPath = uri.getSchemeSpecificPart();
            inputStream = GpgSignatureValidator.class.getClassLoader().getResourceAsStream(keyPath);
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.tracef("Downloading the public key from %s.", uri);
            }
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

    private static class NoopListener implements GpgSignatureValidatorListener {

        @Override
        public void artifactSignatureCorrect(ArtifactIdentifier artifact, PGPPublicKey publicKey) {
            // noop
        }

        @Override
        public void artifactSignatureInvalid(ArtifactIdentifier artifact, PGPPublicKey publicKey) {
            // noop
        }
    }
}
