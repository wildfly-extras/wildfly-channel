/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import java.util.ArrayList;
import java.util.List;

class ManifestBuilder {

    private String id;
    private String logicalVersion;
    private List<ManifestRequirement> requirements = new ArrayList<>();
    private List<Stream> streams = new ArrayList<>();

    ChannelManifest build() {
        return new ChannelManifest(null, id, logicalVersion, null, requirements, streams);
    }

    ManifestBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public ManifestBuilder setLogicalVersion(String logicalVersion) {
        this.logicalVersion = logicalVersion;
        return this;
    }

    ManifestBuilder addRequires(String requiredId) {
        requirements.add(new ManifestRequirement(requiredId, null));
        return this;
    }

    ManifestBuilder addStream(String groupId, String artifactId, String version) {
        streams.add(new Stream(groupId, artifactId, version));
        return this;
    }

    public ManifestBuilder addRequires(String requiredId, String groupId, String artifactId, String version) {
        requirements.add(new ManifestRequirement(requiredId, new MavenCoordinate(groupId, artifactId, version)));
        return this;
    }
}
