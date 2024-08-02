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

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.List;

/**
 * Retrieves a public key from a remote GPG keyserver using a PKS query
 */
public class Keyserver {

    private static final String LOOKUP_PATH = "/pks/lookup";

    private final List<URL> servers;

    public Keyserver(List<URL> serverUrls) {
        this.servers = serverUrls;
    }

    /**
     * download a public key matching the {@code keyID} from one of defined GPG keyservers
     *
     * @param keyID - hex representation of a GPG public key
     * @return - the public key associated with the {@code keyID} or null if not found
     * @throws PGPException
     * @throws IOException
     */
    public PGPPublicKeyRing downloadKey(String keyID) throws PGPException, IOException {
        for (URL server : servers) {
            final PGPPublicKeyRing publicKey = tryDownloadKey(server, keyID);
            if (publicKey != null) {
                return publicKey;
            }
        }
        return null;
    }

    private PGPPublicKeyRing tryDownloadKey(URL serverUrl, String keyID) throws IOException, PGPException {
        final String protocol;
        if (serverUrl.getProtocol().equals("hkps")) {
            protocol = "https";
        } else if (serverUrl.getProtocol().equals("hkp")) {
            protocol = "http";
        } else {
            protocol = serverUrl.getProtocol();
        }

        final String host = serverUrl.getHost();
        final int port = serverUrl.getPort();
        final String path = serverUrl.getPath();

        final URI keyUri = URI.create(protocol + "://" + host + ":" + port + "/" + path + LOOKUP_PATH + "?" + getQueryStringForGetKey(keyID));

        final HttpUriRequest request = new HttpGet(keyUri);

        try (final CloseableHttpClient client = HttpClientBuilder.create().build();
             final CloseableHttpResponse response = client.execute(request)) {
            if (response.getStatusLine().getStatusCode() == 200) {

                final HttpEntity responseEntity = response.getEntity();
                try (InputStream inputStream = responseEntity.getContent()) {
                    final InputStream keyIn = PGPUtil.getDecoderStream(inputStream);
                    final PGPPublicKeyRingCollection pgpRing = new PGPPublicKeyRingCollection(keyIn, new BcKeyFingerprintCalculator());
                    final BigInteger bi = new BigInteger(keyID, 16);
                    return pgpRing.getPublicKeyRing(bi.longValue());
                }
            } else {
                return null;
            }
        }
    }

    private static String getQueryStringForGetKey(String keyID) {
        return String.format("op=get&options=mr&search=0x%s", keyID);
    }
}
