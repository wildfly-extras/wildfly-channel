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

import org.bouncycastle.openpgp.PGPPublicKey;

import java.util.List;

/**
 * Local store of trusted public keys.
 *
 * Note: the keystore can reject a public key being added. In such case, the {@code GpgSignatureValidator} has to reject this key.
 */
public interface GpgKeystore {

    /**
     * resolve a public key from the store.
     *
     * @param keyID - a HEX form of the key ID
     * @return - the resolved public key or {@code null} if the key was not found
     */
    PGPPublicKey get(String keyID);

    /**
     * records the public keys in the store for future use.
     *
     * @param publicKey - list of trusted public keys
     * @return true if the public keys have been added successfully
     *         false otherwise.
     * @throws KeystoreOperationException if the keystore threw an error during the operation
     */
    boolean add(List<PGPPublicKey> publicKey) throws KeystoreOperationException;
}
