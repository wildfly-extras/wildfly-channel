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

package org.wildfly.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.MalformedURLException;
import java.net.URL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * A channel manifest coordinate. Uses either a Maven coordinates (groupId, artifactId, version)
 * or a URL from which the channel manifest file can be fetched.
 */
@JsonIgnoreProperties(value = {"groupId", "artifactId", "version", "classifier", "extension"})
public class ChannelManifestCoordinate extends ChannelMetadataCoordinate {
    public ChannelManifestCoordinate(String groupId, String artifactId, String version) {
        super(groupId, artifactId, version, ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION);
    }

    public ChannelManifestCoordinate(String groupId, String artifactId) {
        super(groupId, artifactId, ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION);
    }

    public ChannelManifestCoordinate(URL url) {
        super(url);
    }

    @JsonCreator
    public static ChannelManifestCoordinate create(@JsonProperty(value = "url") String url, @JsonProperty(value = "maven") MavenCoordinate gav) throws MalformedURLException {
        if (gav != null) {
            if (gav.getVersion() == null || gav.getVersion().isEmpty()) {
                return new ChannelManifestCoordinate(gav.getGroupId(), gav.getArtifactId());
            } else {
                return new ChannelManifestCoordinate(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            }
        } else {
            return new ChannelManifestCoordinate(new URL(url));
        }
    }

    @JsonProperty(value = "maven")
    @JsonInclude(NON_NULL)
    public MavenCoordinate getMaven() {
        if (isEmpty(getGroupId()) || isEmpty(getArtifactId())) {
            return null;
        }
        return new MavenCoordinate(getGroupId(), getArtifactId(), getVersion());
    }

    private boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    @JsonProperty(value = "url")
    @JsonInclude(NON_NULL)
    @Override
    public URL getUrl() {
        return super.getUrl();
    }
}
