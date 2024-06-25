package org.wildfly.channel.gpg;

import org.bouncycastle.openpgp.PGPPublicKey;

import java.util.List;

public interface GpgKeystore {

    PGPPublicKey get(String keyID);

    boolean add(List<PGPPublicKey> publicKey);
}
