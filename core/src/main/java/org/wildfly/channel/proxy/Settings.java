package org.wildfly.channel.proxy;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.auth.UsernamePasswordCredentials;

class Settings {

    private String host = null;
    private int port = -1;

    private String protocol;

    private Credentials credentials = null;

    public Settings() {
    }

    public Settings(String host, int port, String protocol) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
    }

    public Settings(String host, int port, String protocol, Credentials credentials) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.credentials = credentials;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    public boolean hasCredentials() {
        return this.credentials != null;
    }

    public boolean isEmpty() {
        return host == null || port == -1;
    }

    static class Credentials {
        private String username;
        private String password;

        public Credentials() {
        }

        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String toBase64String() {
            return Base64.encodeBase64String((username + ":" + password).getBytes());
        }

        public String toBasicAuthorization() {
            return "Basic " + toBase64String();
        }

        public UsernamePasswordCredentials asCredential() {
            return new UsernamePasswordCredentials(username, password);
        }

    }
}
