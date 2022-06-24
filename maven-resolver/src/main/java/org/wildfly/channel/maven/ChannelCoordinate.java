/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.channel.maven;

import java.net.URL;
import java.util.Objects;

/**
 * A channel coordinate either use Maven coordinates (groupId, artifactId, version)
 * or it uses a URL from which the channel definition file can be fetched.
 */
public class ChannelCoordinate {
    // raw Channel file from an URL
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final URL url;

    public ChannelCoordinate(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null);
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(version);
    }

    public ChannelCoordinate(String groupId, String artifactId) {
        this(groupId, artifactId, null, null);
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);
    }

    public ChannelCoordinate(URL url) {
        this(null, null, null, url);
        Objects.requireNonNull(url);
    }

    private ChannelCoordinate(String groupId, String artifactId, String version, URL url) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getExtension() {
        return "yaml";
    }

    public String getClassifier() {
        return "channel";
    }
}