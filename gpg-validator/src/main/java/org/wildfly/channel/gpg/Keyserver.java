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
import java.util.List;

public class Keyserver {

    private static final String LOOKUP_PATH = "/pks/lookup";

    private final List<String> servers;

    public Keyserver(List<String> servers) {
        this.servers = servers;
    }

    public PGPPublicKeyRing downloadKey(String keyID) throws PGPException, IOException {
        for (String server : servers) {
            final PGPPublicKeyRing publicKey = tryDownloadKey(server, keyID);
            if (publicKey != null) {
                return publicKey;
            }
        }
        return null;
    }

    private PGPPublicKeyRing tryDownloadKey(String server, String keyID) throws IOException, PGPException {
        if (server.startsWith("hkps")) {
            server = server.replace("htps", "https");
        }

        final URI keyUri = URI.create(server + LOOKUP_PATH + "?" + getQueryStringForGetKey("567E347AD0044ADE55BA8A5F199E2F91FD431D51"));

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
