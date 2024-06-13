package org.wildfly.channel.gpg;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.spi.SignatureValidator;

public class GpgSignatureValidator implements SignatureValidator {

    private final GpgKeystore keystore;

    private SignatureValidatorListener listener = new SignatureValidatorListener() {

        @Override
        public void artifactSignatureCorrect(MavenArtifact artifact, PGPPublicKey publicKey) {
            // noop
        }
    };

    public GpgSignatureValidator(GpgKeystore keystore) {
        this.keystore = keystore;
    }

    public void addListener(SignatureValidatorListener listener) {
        this.listener = listener;
    }

    @Override
    public void validateSignature(MavenArtifact artifact, File signature, List<String> gpgUrls) throws IOException, SignatureException {
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(signature);

        final PGPSignature pgpSignature = readSignatureFile(signature);

        final String keyID = Long.toHexString(pgpSignature.getKeyID()).toUpperCase(Locale.ROOT);
        final String artifactGav = String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getVersion());

        final PGPPublicKey publicKey;
        if (keystore.get(keyID) != null) {
            publicKey = keystore.get(keyID);
        } else {
            PGPPublicKey key = null;
            List<PGPPublicKey> pgpPublicKeys = null;
            for (String gpgUrl : gpgUrls) {
                pgpPublicKeys = downloadPublicKey(gpgUrl);
                if (pgpPublicKeys.stream().anyMatch(k -> k.getKeyID() == pgpSignature.getKeyID())) {
                    key = pgpPublicKeys.stream().filter(k -> k.getKeyID() == pgpSignature.getKeyID()).findFirst().get();
                    break;
                }
            }
            if (key == null) {
                throw new SignatureException(String.format(
                        "No matching trusted certificate found to verify signature of artifact %s. Required key ID %s",
                        artifactGav, keyID));
            } else {
                if (keystore.add(pgpPublicKeys)) {
                    publicKey = key;
                } else {
                    throw new SignatureException(String.format(
                            "No matching trusted certificate found to verify signature of artifact %s. Required key ID %s",
                            artifactGav, keyID));
                }
            }
        }

        final Iterator<PGPSignature> subKeys = publicKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
        while (subKeys.hasNext()) {
            final PGPSignature subKey = subKeys.next();
            final PGPPublicKey masterKey = keystore.get(Long.toHexString(subKey.getKeyID()).toUpperCase(Locale.ROOT));
            if (masterKey.hasRevocation()) {
                throw new SignatureException(String.format(
                        "The certificate (key ID %s) used to sign artifact %s has been revoked " +
                                "with message:%n %s.",
                        artifactGav, keyID, getRevocationReason(masterKey)));
            }
        }

        if (publicKey.hasRevocation()) {
            throw new SignatureException(
                    String.format("The certificate (key ID %s) used to sign artifact %s has been revoked with message:%n%s.",
                            keyID, artifactGav, getRevocationReason(publicKey)));
        }

        if (publicKey.getValidSeconds() > 0) {
            final Instant expiry = Instant.from(publicKey.getCreationTime().toInstant().plus(publicKey.getValidSeconds(), ChronoUnit.SECONDS));
            if (expiry.isBefore(Instant.now())) {
                throw new SignatureException(
                        String.format("The certificate (key ID %s) used to sign artifact %s has expired.",
                                keyID, artifactGav));
            }
        }

        try {
            pgpSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);
        } catch (PGPException e) {
            throw new RuntimeException(e);
        }

        verifyFile(artifact, pgpSignature);

        listener.artifactSignatureCorrect(artifact, publicKey);
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

    private static void verifyFile(MavenArtifact mavenArtifact, PGPSignature pgpSignature) {
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
                throw new SignatureException(String.format(
                        "The signature for artifact %s:%s:%s is invalid. The artifact might be corrupted or tampered with.",
                        mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getVersion()));
            }
        } catch (PGPException e) {
            throw new SignatureException("Unable to verify the file signature", e);
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
            } else {
                throw new SignatureException("Could not find signature in provided signature file");
            }
        }
        return pgpSignature;
    }

    private static List<PGPPublicKey> downloadPublicKey(String signatureUrl) {
        try {
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

        } catch (IOException e) {
            throw new SignatureException("Unable to parse the certificate downloaded from " + signatureUrl, e);
        }
    }

    public interface SignatureValidatorListener {

        void artifactSignatureCorrect(MavenArtifact artifact, PGPPublicKey publicKey);
    }
}
