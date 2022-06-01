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

import java.util.Collections;

class ChannelRecorder {

    final Channel recordedChannel = new Channel(null,
            null,
            null,
            null,
            Collections.emptyList());

    void recordStream(String groupId, String artifactId, String version) {
        boolean isRecorded = recordedChannel.getStreams().stream().anyMatch(s -> s.getGroupId().equals(groupId) && s.getArtifactId().equals(artifactId) && s.getVersion().equals(version));
        if (!isRecorded) {
            recordedChannel.addStream(new Stream(groupId, artifactId, version, null, null));
        }
    }

    Channel getRecordedChannel() { return recordedChannel; }
}
