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

import org.assertj.core.api.Assertions;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.util.io.Streams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.pgpainless.PGPainless;
import org.pgpainless.algorithm.KeyFlag;
import org.pgpainless.encryption_signing.EncryptionStream;
import org.pgpainless.encryption_signing.ProducerOptions;
import org.pgpainless.encryption_signing.SigningOptions;
import org.pgpainless.key.SubkeyIdentifier;
import org.pgpainless.key.generation.KeySpec;
import org.pgpainless.key.generation.type.KeyType;
import org.pgpainless.key.generation.type.rsa.RsaLength;
import org.pgpainless.key.protection.UnprotectedKeysProtector;
import org.pgpainless.key.util.RevocationAttributes;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GpgSignatureValidatorTest {
    @TempDir
    Path tempDir;

    private PGPSecretKeyRing pgpValidKeys;
    private PGPSecretKeyRing pgpAttackerKeys;
    private PGPSecretKeyRing pgpExpiredKeys;
    private TestKeystore keystore = new TestKeystore();
    private GpgSignatureValidator validator;
    private MavenArtifact anArtifact;
    private File signatureFile;

    @BeforeEach
    public void setUp() throws Exception {
        pgpValidKeys = PGPainless.generateKeyRing().simpleRsaKeyRing("Test <test@test.org>", RsaLength._4096);
        pgpAttackerKeys = PGPainless.generateKeyRing().simpleRsaKeyRing("Fake <fake@test.org>", RsaLength._4096);
        pgpExpiredKeys = PGPainless.buildKeyRing()
                .setPrimaryKey(KeySpec.getBuilder(KeyType.RSA(RsaLength._4096), KeyFlag.CERTIFY_OTHER, KeyFlag.SIGN_DATA, KeyFlag.ENCRYPT_COMMS))
                .addUserId("Test <test@test.org>")
                .setExpirationDate(new Date(System.currentTimeMillis()+1_000))
                .build();

        keystore = new TestKeystore();
        validator = new GpgSignatureValidator(keystore);

        File artifactFile = tempDir.resolve("test-one.jar").toFile();
        Files.writeString(artifactFile.toPath(), "test");
        anArtifact = new MavenArtifact("org.test", "test-one", "jar", null, "1.0.0", artifactFile);

        signatureFile = signFile(anArtifact.getFile(), pgpValidKeys);
    }

    @Test
    public void validSignatureIsAccepted() throws Exception {
        keystore.using(pgpValidKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, Collections.emptyList()))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.OK);
    }

    @Test
    public void invalidSignatureReturnsErrorStatus() throws Exception {
        keystore.using(pgpValidKeys);

        final File signatureFile = signFile(anArtifact.getFile(), pgpAttackerKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, Collections.emptyList()))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.NO_MATCHING_CERT)
                .hasFieldOrPropertyWithValue("artifact", toCoord());
    }

    @Test
    public void expiredSignatureReturnsError() throws Exception {
        keystore.using(pgpExpiredKeys);

        // the certificate has to have an expiry date at least now()+1 second, otherwise it's treated as never-expiring
        // wait for certificate to expire
        while (!isExpired(pgpExpiredKeys.getPublicKey())) {
            Thread.sleep(100);
        }

        final File signatureFile = signFile(anArtifact.getFile(), pgpExpiredKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, Collections.emptyList()))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.EXPIRED)
                .hasFieldOrPropertyWithValue("artifact", toCoord())
                .hasFieldOrPropertyWithValue("keyId", toHex(pgpExpiredKeys.getPublicKey().getKeyID()));
    }

    @Test
    public void revokedSignatureReturnsError() throws Exception {
        // order of operations matter! sign artifact, revoke the key, init the keystore
        final PGPSecretKeyRing pgpExpiredKeys = PGPainless.modifyKeyRing(pgpValidKeys)
                .revoke(new UnprotectedKeysProtector(),
                        RevocationAttributes
                                .createKeyRevocation()
                                .withReason(RevocationAttributes.Reason.KEY_COMPROMISED)
                                .withDescription("The key is revoked"))
                .done();
        keystore.using(pgpExpiredKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, Collections.emptyList()))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.REVOKED)
                .hasFieldOrPropertyWithValue("artifact", toCoord())
                .hasFieldOrPropertyWithValue("keyId", toHex(pgpValidKeys.getPublicKey().getKeyID()))
                .hasFieldOrPropertyWithValue("message", "The key is revoked");
    }

    @Test
    public void downloadsSignatureIfUrlIsProvided() throws Exception {
        keystore.using(Collections.emptyList());

        // export the public certificate
        final File publicCertFile = exportPublicCertificate(pgpValidKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, List.of(publicCertFile.toURI().toString())))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.OK);

        Assertions.assertThat(keystore.getKeys().keySet())
                .containsOnly(toHex(pgpValidKeys.getPublicKey().getKeyID()));
    }

    @Test
    public void failedSignatureDownloadThrowsException() throws Exception {
        keystore.using(Collections.emptyList());

        // export the public certificate
        final File publicCertFile = tempDir.resolve("public.crt").toFile();
        Files.writeString(publicCertFile.toPath(), "I'm not a certificate");
        final String certUrl = publicCertFile.toURI().toString();

        Assertions.assertThatThrownBy(()->validator.validateSignature(anArtifact, signatureFile, List.of(certUrl)))
                .isInstanceOf(SignatureValidator.SignatureException.class)
                .hasMessageContainingAll("Unable to parse the certificate downloaded from " + certUrl);
    }

    @Test
    public void invalidSignatureDownloadedReturnsError() throws Exception {
        keystore.using(Collections.emptyList());

        final File signatureFile = signFile(anArtifact.getFile(), pgpAttackerKeys);

        // export the public certificate
        final File publicCertFile = exportPublicCertificate(pgpValidKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, List.of(publicCertFile.toURI().toString())))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.NO_MATCHING_CERT)
                .hasFieldOrPropertyWithValue("artifact", toCoord())
                .hasFieldOrPropertyWithValue("keyId", toHex(pgpAttackerKeys.getPublicKey().getKeyID()));

        // no certificates should have been imported
        Assertions.assertThat(keystore.getKeys().keySet())
                .isEmpty();
    }

    @Test
    public void keystoreRejectingCertificateReturnsError() throws Exception {
        final GpgKeystore rejectingKeystore = Mockito.mock(GpgKeystore.class);
        Mockito.when(rejectingKeystore.add(Mockito.anyList())).thenReturn(false);
        validator = new GpgSignatureValidator(rejectingKeystore);

        final File publicCertFile = exportPublicCertificate(pgpValidKeys);

        Assertions.assertThat(validator.validateSignature(anArtifact, signatureFile, List.of(publicCertFile.toURI().toString())))
                .hasFieldOrPropertyWithValue("result", SignatureResult.Result.NO_MATCHING_CERT)
                .hasFieldOrPropertyWithValue("artifact", toCoord())
                .hasFieldOrPropertyWithValue("keyId", toHex(pgpValidKeys.getPublicKey().getKeyID()));
    }

    private ArtifactCoordinate toCoord() {
        return new ArtifactCoordinate(anArtifact.getGroupId(), anArtifact.getArtifactId(), anArtifact.getExtension(),
                anArtifact.getClassifier(), anArtifact.getVersion());
    }

    private File exportPublicCertificate(PGPSecretKeyRing keyRing) throws IOException {
        // export the public certificate
        final File publicCertFile = tempDir.resolve("public.crt").toFile();
        try (ArmoredOutputStream outStream = new ArmoredOutputStream(new FileOutputStream(publicCertFile))) {
            keyRing.getPublicKey().encode(outStream);
        }
        return publicCertFile;
    }

    private boolean isExpired(PGPPublicKey publicKey) {
        if (publicKey.getValidSeconds() == 0) {
            return false;
        } else {
            final Instant expiry = Instant.from(publicKey.getCreationTime().toInstant().plus(publicKey.getValidSeconds(), ChronoUnit.SECONDS));
            return expiry.isBefore(Instant.now());
        }
    }

    private File signFile(File file, PGPSecretKeyRing pgpSecretKeys) throws PGPException, IOException {
        final SigningOptions signOptions = SigningOptions.get()
                .addDetachedSignature(new UnprotectedKeysProtector(), pgpSecretKeys);

        final File signatureFile = tempDir.resolve("test-one.jar.asc").toFile();
        final EncryptionStream encryptionStream = PGPainless.encryptAndOrSign()
                .onOutputStream(new FileOutputStream(signatureFile))
                .withOptions(ProducerOptions.sign(signOptions));

        Streams.pipeAll(new FileInputStream(file), encryptionStream); // pipe the data through
        encryptionStream.close();

        // wrap signature in armour
        try(FileOutputStream fos = new FileOutputStream(signatureFile);
            final ArmoredOutputStream aos = new ArmoredOutputStream(fos)) {
            for (SubkeyIdentifier subkeyIdentifier : encryptionStream.getResult().getDetachedSignatures().keySet()) {
                final Set<PGPSignature> pgpSignatures = encryptionStream.getResult().getDetachedSignatures().get(subkeyIdentifier);
                for (PGPSignature pgpSignature : pgpSignatures) {
                    pgpSignature.encode(aos);
                }
            }
        }
        return signatureFile;
    }

    private static class TestKeystore implements GpgKeystore {

        private final HashMap<String, PGPPublicKey> keys = new HashMap<>();

        TestKeystore() {

        }

        public void using(PGPSecretKeyRing pgpSecretKeys) {
            this.using(PGPainless.extractCertificate(pgpSecretKeys));
        }

        void using(PGPPublicKeyRing pgpPublicKeys) {
            keys.clear();

            final Iterator<PGPPublicKey> publicKeys = pgpPublicKeys.getPublicKeys();
            while (publicKeys.hasNext()) {
                final PGPPublicKey key = publicKeys.next();
                keys.put(toHex(key.getKeyID()), key);
            }
        }

        public void using(List<PGPPublicKey> publicKeys) {
            keys.clear();

            for (PGPPublicKey key : publicKeys) {
                keys.put(toHex(key.getKeyID()), key);
            }
        }

        public HashMap<String, PGPPublicKey> getKeys() {
            return keys;
        }

        @Override
        public PGPPublicKey get(String keyID) {
            return keys.get(keyID);
        }

        @Override
        public boolean add(List<PGPPublicKey> publicKeys) {
            for (PGPPublicKey key : publicKeys) {
                keys.put(toHex(key.getKeyID()), key);
            }
            return true;
        }
    }

    private static String toHex(long keyID) {
        return Long.toHexString(keyID).toUpperCase(Locale.ROOT);
    }

}