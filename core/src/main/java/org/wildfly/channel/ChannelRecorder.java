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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ChannelRecorder {
    private ConcurrentHashMap<String, Stream> streams = new ConcurrentHashMap<>();

    void recordStream(String groupId, String artifactId, String version, String extension, String classifier, String sha256Checksum) {
        Map<String, String> sha256CheckSums = new HashMap<>();
        if (extension != null && sha256Checksum != null) {
            String key = classifier == null || classifier.isEmpty() ? extension : classifier+"/"+extension;
            sha256CheckSums.put(key, sha256Checksum);
        }
        String key = groupId + ":" + artifactId + ":" + version;
        Stream stream = streams.get(key);
        if (stream != null) {
           stream.getsha256Checksum().putAll(sha256CheckSums);
        } else {
            streams.put(key, new Stream(groupId, artifactId, version, null, sha256CheckSums));
        }
    }

    ChannelManifest getRecordedChannel() {
        return new ChannelManifest(null, null, null, new ArrayList<Stream>(streams.values()));
    }
}
