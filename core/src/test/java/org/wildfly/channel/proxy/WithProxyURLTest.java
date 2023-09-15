package org.wildfly.channel.proxy;

import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit5.HoverflyExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;
import java.net.URL;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(HoverflyExtension.class)
public class WithProxyURLTest {

    @BeforeAll
    static void beforeAll() {
        HttpProxy.setup();
    }

    @BeforeEach
    void setUp() {
        HttpProxy.cleanupSystemProperties();
    }

    @AfterEach
    void tearDown() {
        HttpProxy.cleanupSystemProperties();
    }


    @Test
    void unauthenticatedHttp(Hoverfly hoverfly) throws Exception {
        String expected = "bar";
        hoverfly.simulate(dsl(
                service("fuu.bar:80")
                        .get("/fuu")
                        .willReturn(success().body(expected))
        ));
        Settings settings = new Settings(
                "localhost",
                hoverfly.getHoverflyConfig().getProxyPort(),
                "http"
        );
        SettingsFactory.HTTP.setSystemProperties(settings);

        URL url = new URL("http://fuu.bar/fuu");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        url.openStream().transferTo(outputStream);
        String result = outputStream.toString();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void authenticatedHttp(Hoverfly hoverfly) throws Exception {
        Settings.Credentials credentials = new Settings.Credentials(
                "username",
                "password"
        );
        String expected = "bar";
        hoverfly.simulate(dsl(
                service("fuu.bar:80")
                        .get("/fuu")
                        .header(HttpProxy.PROXY_AUTHORIZATION, credentials.toBasicAuthorization())
                        .willReturn(success().body(expected))
        ));
        hoverfly.getHoverflyConfig().getDestination();
        Settings settings = new Settings(
                "localhost",
                hoverfly.getHoverflyConfig().getProxyPort(),
                "http",
                credentials
        );
        SettingsFactory.HTTP.setSystemProperties(settings);

        URL url = new URL("http://fuu.bar/fuu");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        url.openStream().transferTo(outputStream);
        String result = outputStream.toString();
        assertThat(result).isEqualTo(expected);
    }
    

}
