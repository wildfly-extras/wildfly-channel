package org.wildfly.channel.proxy;

import org.junit.jupiter.api.Test;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpUrlStreamHandlerFactoryTest {

    @Test
    public void canCreate() {
        URLStreamHandlerFactory handlerFactory = new HttpUrlStreamHandlerFactory();
        assertThat(handlerFactory).isNotNull();
    }

    @Test
    public void shouldHandleOnlyHttpAndHttps() {
        HttpUrlStreamHandlerFactory handlerFactory = new HttpUrlStreamHandlerFactory();
        URLStreamHandler httpHandler = handlerFactory.createURLStreamHandler("http");
        assertThat(httpHandler).isInstanceOf(URLStreamHandler.class);
        URLStreamHandler httpsHandler = handlerFactory.createURLStreamHandler("https");
        assertThat(httpsHandler).isInstanceOf(URLStreamHandler.class);
        for (String protocol : List.of("ftp", "nntp", "file")) {
            URLStreamHandler handler = handlerFactory.createURLStreamHandler(protocol);
            assertThat(handler).isNull();
        }
    }
}