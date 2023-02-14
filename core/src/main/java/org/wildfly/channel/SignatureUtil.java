/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.channel;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

public class SignatureUtil {

    static void verifySignature(
            Path dataFile,
            Path signatureFile,
            URL publicKey)
            throws SignatureException, IOException {
        try (InputStream keyIn = publicKey.openStream();
                InputStream signIn = new FileInputStream(signatureFile.toFile());
                InputStream dataFileIn = new FileInputStream(dataFile.toFile())) {
            verifySignature(dataFileIn, signIn, keyIn);
        }
    }

    private static void verifySignature(
            InputStream dataFileIn,
            InputStream signIn,
            InputStream keyIn)
            throws SignatureException, IOException {
        //Obtains a stream that can be used to read PGP data from the provided stream.
        try (InputStream pgpSignatureIn = PGPUtil.getDecoderStream(signIn);
                InputStream pgpKeyIn = PGPUtil.getDecoderStream(keyIn)) {

            // Construct an object factory to read PGP objects from a stream.
            JcaPGPObjectFactory pgpFactory = new JcaPGPObjectFactory(pgpSignatureIn);
            PGPSignatureList signatureList;

            Object obj = pgpFactory.nextObject();
            // Signature could have been compressed
            if (obj instanceof PGPCompressedData) {
                PGPCompressedData compressed = (PGPCompressedData) obj;
                pgpFactory = new JcaPGPObjectFactory(compressed.getDataStream());
                signatureList = (PGPSignatureList) pgpFactory.nextObject();
            } else {
                signatureList = (PGPSignatureList) obj;
            }

            PGPPublicKeyRingCollection pgpPublicRingCollection
                    = new PGPPublicKeyRingCollection(pgpKeyIn, new JcaKeyFingerprintCalculator());

            PGPSignature signature = signatureList.get(0);
            PGPPublicKey publicKey = pgpPublicRingCollection.getPublicKey(signature.getKeyID());

            signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(new BouncyCastleProvider()), publicKey);

            int ch;
            while ((ch = dataFileIn.read()) >= 0) {
                signature.update((byte) ch);
            }

            if (!signature.verify()) {
                throw new SignatureException("Signature verification failed");
            }
        } catch (PGPException ex) {
            throw new SignatureException("Exception verifying signature", ex);
        }
    }
}
