package org.wildfly.channel.proxy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
public class WithoutProxyURLTest {

    @BeforeAll
    static void beforeAll() {
        HttpProxy.setup();
    }

    @BeforeEach
    void setUp() {
        HttpProxy.cleanupSystemProperties();
    }

    @Test
    void testBasicHttpUrl(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        String expected = "bar";
        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(get("/fuu").willReturn(ok(expected)));
        URL url = new URL(wmRuntimeInfo.getHttpBaseUrl() + "/fuu");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        url.openStream().transferTo(outputStream);
        String result = outputStream.toString();
        assertThat(result).isEqualTo(expected);
    }


}
