package org.wildfly.channel.proxy;


import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.wildfly.channel.proxy.HttpProxy.*;

enum SettingsFactory {

    HTTP(
            HTTP_PROXY_HOST,
            HTTP_PROXY_PORT,
            HTTP_PROXY_USERNAME,
            HTTP_PROXY_PASSWORD,
            HTTP_PROXY_PROTOCOL,
            "http",
            80
    ),
    HTTPS(
            HTTPS_PROXY_HOST,
            HTTPS_PROXY_PORT,
            HTTPS_PROXY_USERNAME,
            HTTPS_PROXY_PASSWORD,
            HTTPS_PROXY_PROTOCOL,
            "https",
            443
    );

    private String host;
    private String port;
    private String username;
    private String password;

    private String protocol;

    private String defaultProtocol;
    private int defaultPort;

    SettingsFactory(String host, String port, String username, String password, String protocol, String defaultProtocol, int defaultPort) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.protocol = protocol;
        this.defaultProtocol = defaultProtocol;
        this.defaultPort = defaultPort;
    }

    public Settings createFromProperties(Properties properties) {
        String proxyHost = properties.getProperty(host);
        int proxyPort = Optional.ofNullable(properties.getProperty(port)).map(Integer::parseInt).orElse(defaultPort);
        String proxyProtocol = Optional.ofNullable(properties.getProperty(protocol)).orElse(defaultProtocol);
        String proxyUsername = properties.getProperty(username);
        String proxyPassword = properties.getProperty(password);
        Settings.Credentials credentials = null;
        if (proxyUsername != null && proxyPassword != null) {
            credentials = new Settings.Credentials(proxyUsername, proxyPassword);
        }
        return new Settings(proxyHost, proxyPort, proxyProtocol, credentials);
    }

    public Settings createFromSystemProperties() {
        return createFromProperties(System.getProperties());
    }

    public void setProperties(Settings settings, Properties properties) {
        properties.setProperty(host, settings.getHost());
        properties.setProperty(port, String.valueOf(settings.getPort()));
        properties.setProperty(protocol, settings.getProtocol());
        if (settings.hasCredentials()) {
            Settings.Credentials credentials = settings.getCredentials();
            properties.setProperty(username, credentials.getUsername());
            properties.setProperty(password, credentials.getPassword());
        }
    }

    public void setSystemProperties(Settings settings) {
        setProperties(settings, System.getProperties());
    }

    public void cleanupProperties(Properties properties) {
        for (String key : List.of(host, port, username, password)) {
            properties.remove(key);
        }
    }

    public String getProtocol() {
        return name().toLowerCase();
    }

    
}