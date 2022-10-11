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

class ChannelBuilder {
    private String name;
    private List<Repository> repositories = new ArrayList<>();
    private ChannelManifestCoordinate manifestCoordinate;
    private BlocklistCoordinate blocklistCoordinate;
    private Channel.NoStreamStrategy strategy;

    Channel build() {
        return new Channel(name, null, null, repositories, manifestCoordinate, blocklistCoordinate, strategy);
    }

    ChannelBuilder setName(String name) {
        this.name = name;
        return this;
    }

    ChannelBuilder addRepository(String repoId, String url) {
        repositories.add(new Repository(repoId, url));
        return this;
    }

    public ChannelBuilder setManifestCoordinate(String groupId, String artifactId, String version) {
        this.manifestCoordinate = new ChannelManifestCoordinate(groupId, artifactId, version);
        return this;
    }

    public ChannelBuilder setBlocklist(String groupId, String artifactId, String version) {
        if (version == null) {
            this.blocklistCoordinate = new BlocklistCoordinate(groupId, artifactId);
        } else {
            this.blocklistCoordinate = new BlocklistCoordinate(groupId, artifactId, version);
        }
        return this;
    }

    public ChannelBuilder setResolveStrategy(Channel.NoStreamStrategy strategy) {
        this.strategy = strategy;
        return this;
    }
}
