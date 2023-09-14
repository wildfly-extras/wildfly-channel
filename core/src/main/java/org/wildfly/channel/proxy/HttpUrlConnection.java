package org.wildfly.channel.proxy;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

class HttpUrlConnection extends URLConnection {


    private HttpClient client;
    private HttpHost host;
    private HttpRequest request;


    public HttpUrlConnection(URL url, HttpClient client, HttpHost host, HttpRequest request) {
        super(url);
        this.client = client;
        this.host = host;
        this.request = request;
    }

    private boolean connected;

    private HttpResponse response = null;

    private final ReentrantLock connectionLock = new ReentrantLock();

    private void lock() {
        connectionLock.lock();
    }

    private void unlock() {
        connectionLock.unlock();
    }

    @Override
    public void connect() throws IOException {
        lock();
        try {
            if (connected) return;
            for (Map.Entry<String, List<String>> header : getRequestProperties().entrySet()) {
                for (String value : header.getValue()) {
                    request.addHeader(header.getKey(), value);
                }
            }
            response = client.execute(host, request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                OutputStream outputStream = new ByteArrayOutputStream();
                response.getEntity().getContent().transferTo(outputStream);
                String errorMessage = outputStream.toString();
                throw new IOException("Fail to get " + url + ": " + response.getStatusLine() + "\n" + errorMessage);
            }


            this.connected = true;
        } finally {
            unlock();
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected) {
            connect();
        }
        return response.getEntity().getContent();
    }

}
