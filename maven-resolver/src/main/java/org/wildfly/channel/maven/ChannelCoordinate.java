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

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMetadataCoordinate;

import java.net.URL;

/**
 * A channel coordinate either use Maven coordinates (groupId, artifactId, version)
 * or it uses a URL from which the channel definition file can be fetched.
 */
public class ChannelCoordinate extends ChannelMetadataCoordinate {

    // empty constructor used by the wildlfy-maven-plugin
    // through reflection
    public ChannelCoordinate() {
        super(Channel.CLASSIFIER, Channel.EXTENSION);
    }

    public ChannelCoordinate(String groupId, String artifactId, String version) {
        super(groupId, artifactId, version, Channel.CLASSIFIER, Channel.EXTENSION);
    }

    public ChannelCoordinate(String groupId, String artifactId) {
        super(groupId, artifactId, Channel.CLASSIFIER, Channel.EXTENSION);
    }

    public ChannelCoordinate(URL url) {
        super(url);
    }
}