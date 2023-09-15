package org.wildfly.channel.proxy;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class SettingsTest {

    @Test
    void loadEmptyHttpProperties() {
        Properties properties = new Properties();
        Settings settings = SettingsFactory.HTTP.createFromProperties(properties);
        assertThat(settings.isEmpty()).isTrue();
    }

    @Test
    void loadUnauthenticatedHttpProperties() {
        String host = "localhost";
        int port = 8080;
        Properties properties = new Properties();
        properties.setProperty(HttpProxy.HTTP_PROXY_HOST, host);
        properties.setProperty(HttpProxy.HTTP_PROXY_PORT, String.valueOf(port));
        Settings settings = SettingsFactory.HTTP.createFromProperties(properties);
        assertThat(settings.getHost()).isEqualTo(host);
        assertThat(settings.getPort()).isEqualTo(port);
        assertThat(settings.hasCredentials()).isFalse();
    }

    @Test
    void loadAuthenticatedHttpProperties() {
        String host = "localhost";
        int port = 8080;
        String username = "user";
        String password = "password";
        Properties properties = new Properties();
        properties.setProperty(HttpProxy.HTTP_PROXY_HOST, host);
        properties.setProperty(HttpProxy.HTTP_PROXY_PORT, String.valueOf(port));
        properties.setProperty(HttpProxy.HTTP_PROXY_USERNAME, username);
        properties.setProperty(HttpProxy.HTTP_PROXY_PASSWORD, password);
        Settings settings = SettingsFactory.HTTP.createFromProperties(properties);
        assertThat(settings.getHost()).isEqualTo(host);
        assertThat(settings.getPort()).isEqualTo(port);
        assertThat(settings.hasCredentials()).isTrue();
        Settings.Credentials credentials = settings.getCredentials();
        assertThat(credentials.getUsername()).isEqualTo(username);
        assertThat(credentials.getPassword()).isEqualTo(password);
    }


    @Test
    void loadEmptyHttpsProperties() {
        Properties properties = new Properties();
        Settings settings = SettingsFactory.HTTPS.createFromProperties(properties);
        assertThat(settings.isEmpty()).isTrue();
    }

    @Test
    void loadUnauthenticatedHttpsProperties() {
        String host = "localhost";
        int port = 8080;
        Properties properties = new Properties();
        properties.setProperty(HttpProxy.HTTPS_PROXY_HOST, host);
        properties.setProperty(HttpProxy.HTTPS_PROXY_PORT, String.valueOf(port));
        Settings settings = SettingsFactory.HTTPS.createFromProperties(properties);
        assertThat(settings.getHost()).isEqualTo(host);
        assertThat(settings.getPort()).isEqualTo(port);
        assertThat(settings.hasCredentials()).isFalse();
    }

    @Test
    void loadAuthenticatedHttpsProperties() {
        String host = "localhost";
        int port = 8080;
        String username = "user";
        String password = "password";
        Properties properties = new Properties();
        properties.setProperty(HttpProxy.HTTPS_PROXY_HOST, host);
        properties.setProperty(HttpProxy.HTTPS_PROXY_PORT, String.valueOf(port));
        properties.setProperty(HttpProxy.HTTPS_PROXY_USERNAME, username);
        properties.setProperty(HttpProxy.HTTPS_PROXY_PASSWORD, password);
        Settings settings = SettingsFactory.HTTPS.createFromProperties(properties);
        assertThat(settings.getHost()).isEqualTo(host);
        assertThat(settings.getPort()).isEqualTo(port);
        assertThat(settings.hasCredentials()).isTrue();
        Settings.Credentials credentials = settings.getCredentials();
        assertThat(credentials.getUsername()).isEqualTo(username);
        assertThat(credentials.getPassword()).isEqualTo(password);
    }
}
