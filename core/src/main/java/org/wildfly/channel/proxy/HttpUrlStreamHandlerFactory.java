package org.wildfly.channel.proxy;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;

class HttpUrlStreamHandlerFactory implements URLStreamHandlerFactory {

    private final HttpUrlStreamHandler handler;

    public HttpUrlStreamHandlerFactory() {
        this(new HttpUrlStreamHandler());
    }

    public HttpUrlStreamHandlerFactory(HttpUrlStreamHandler handler) {
        this.handler = handler;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (protocol == null) return null;
        if (Arrays.stream(SettingsFactory.values()).map(SettingsFactory::getProtocol).anyMatch(protocol::equals)) {
            return handler;
        }
        return null;
    }
}
