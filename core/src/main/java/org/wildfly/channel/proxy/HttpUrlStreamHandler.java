package org.wildfly.channel.proxy;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collections;

import static org.wildfly.channel.proxy.HttpProxy.PROXY_AUTHORIZATION;
import static org.wildfly.channel.proxy.HttpProxy.asHttpHost;

class HttpUrlStreamHandler extends URLStreamHandler {

    private HttpClient httpClient;

    public HttpUrlStreamHandler() {
        this(HttpClients.custom().useSystemProperties().build());
    }

    public HttpUrlStreamHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Settings getProxySettings(URL url) {
        SettingsFactory settingsFactory = SettingsFactory.valueOf(url.getProtocol().toUpperCase());
        return settingsFactory.createFromSystemProperties();
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        HttpClient client = httpClient;
        HttpHost targetHost = asHttpHost(url);
        HttpGet request = new HttpGet(url.getPath());
        Settings proxySettings = getProxySettings(url);
        if (proxySettings.isEmpty()) {
            return new HttpUrlConnection(url, client, targetHost, request);
        }
        HttpHost proxyHost = new HttpHost(
                proxySettings.getHost(),
                proxySettings.getPort(),
                proxySettings.getProtocol()
        );
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setProxy(proxyHost);
        request.setConfig(requestConfigBuilder.build());
        if (proxySettings.hasCredentials()) {
            Settings.Credentials credentials = proxySettings.getCredentials();
            BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
            basicCredentialsProvider.setCredentials(AuthScope.ANY, credentials.asCredential());
            requestConfigBuilder.setProxyPreferredAuthSchemes(Collections.singleton(AuthSchemes.BASIC));
            client = HttpClients.custom()
                    .useSystemProperties()
                    .setProxy(proxyHost)
                    .setDefaultCredentialsProvider(basicCredentialsProvider)
                    .setDefaultHeaders(Collections.singleton(new BasicHeader(PROXY_AUTHORIZATION, credentials.toBasicAuthorization())))
                    .build();
        }

        return new HttpUrlConnection(
                url, client, targetHost, request
        );
    }
}
