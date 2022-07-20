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

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.Objects;

/**
 * A channel coordinate either use Maven coordinates (groupId, artifactId, version)
 * or it uses a URL from which the channel definition file can be fetched.
 */
public class ChannelCoordinate {
    private String groupId;
    private String artifactId;
    private String version;
    // raw Channel file from an URL
    private URL url;

    // empty constructor used by the wildlfy-maven-plugin
    // through reflection
    public ChannelCoordinate() {
    }

    public ChannelCoordinate(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null);
        requireNonNull(groupId);
        requireNonNull(artifactId);
        requireNonNull(version);
    }

    public ChannelCoordinate(String groupId, String artifactId) {
        this(groupId, artifactId, null, null);
        requireNonNull(groupId);
        requireNonNull(artifactId);
    }

    public ChannelCoordinate(URL url) {
        this(null, null, null, url);
        requireNonNull(url);
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