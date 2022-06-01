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

import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

class ChannelRecorder {

    final Channel recordedChannel = new Channel(null,
            null,
            null,
            null,
            Collections.emptyList());

    void recordStream(String groupId, String artifactId, String newVersion) {
        Optional<Stream> optStream = recordedChannel.getStreams().stream().filter(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals(artifactId)).findFirst();
        if (!optStream.isPresent()) {
            recordedChannel.addStream(new Stream(groupId, artifactId, newVersion, null, null));
        } else {
            Stream stream = optStream.get();
            String version = stream.getVersion();
            Map<String, Pattern> versions = stream.getVersions();
            if (version != null) {
                if (version.equals(newVersion)) {
                    return;
                }
                versions = new HashMap<>();
                versions.put(quote(version), compile(quote(version)));
            }
            versions.put(quote(newVersion), compile(quote(newVersion)));
            recordedChannel.getStreams().remove(stream);
            recordedChannel.addStream(new Stream(groupId, artifactId, null, null, versions));
        }
    }

    Channel getRecordedChannel() { return recordedChannel; }
}
