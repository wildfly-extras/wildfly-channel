package org.wildfly.channel.proxy;

import org.apache.http.HttpHost;
import org.jboss.logging.Logger;

import java.net.URL;

public abstract class HttpProxy {

    private static final Logger LOG = Logger.getLogger(HttpProxy.class);

    private HttpProxy() {
    }

    /**
     * Http Proxy-Authorization Header
     */
    static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    /**
     * System property to get the http proxy host
     */
    static final String HTTP_PROXY_HOST = "http.proxyHost";

    /**
     * System property to get the http proxy port
     */
    static final String HTTP_PROXY_PORT = "http.proxyPort";


    /**
     * System property to get the authenticated http proxy username
     */
    static final String HTTP_PROXY_USERNAME = "http.proxyUser";

    /**
     * System property to get the authenticated http proxy password
     */
    static final String HTTP_PROXY_PASSWORD = "http.proxyPassword";

    /**
     * System property to get the http proxy protocol
     */
    static final String HTTP_PROXY_PROTOCOL = "http.proxyProtocol";


    /**
     * System property to get the https proxy host
     */
    static final String HTTPS_PROXY_HOST = "https.proxyHost";

    /**
     * System property to get the https proxy port
     */
    static final String HTTPS_PROXY_PORT = "https.proxyPort";

    /**
     * System property to get the authenticated https proxy username
     */
    static final String HTTPS_PROXY_USERNAME = "https.proxyUser";

    /**
     * System property to get the authenticated https proxy password
     */
    static final String HTTPS_PROXY_PASSWORD = "https.proxyPassword";

    /**
     * System property to get the https proxy protocol
     */
    static final String HTTPS_PROXY_PROTOCOL = "https.proxyProtocol";

    public static void setup() {
        try {
            URL.setURLStreamHandlerFactory(new HttpUrlStreamHandlerFactory());
        } catch (Error error) {
            LOG.warn("Unable to URL.setURLStreamHandlerFactory", error);
        }
    }

    static void cleanupSystemProperties() {
        for (SettingsFactory factory : SettingsFactory.values()) {
            factory.cleanupProperties(System.getProperties());
        }
    }

    static HttpHost asHttpHost(URL url) {
        int port = url.getPort();
        if (port <= 0) {
            port = url.getDefaultPort();
        }
        HttpHost host = new HttpHost(url.getHost(), port, url.getProtocol());
        return host;
    }
}
