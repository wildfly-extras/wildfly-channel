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

    void recordStream(String groupId, String artifactId, String version, String extension, String sha1Checksum) {
        System.out.println("groupId = " + groupId + ", artifactId = " + artifactId + ", version = " + version + ", extension = " + extension + ", sha1Checksum = " + sha1Checksum);
        Map sha1CheckSums = new HashMap<String, String>();
        if (extension != null) {
            sha1CheckSums.put(extension, sha1Checksum);
        } else {
            sha1CheckSums.put("wth", sha1Checksum);
        }
        streams.putIfAbsent(groupId + ":" + artifactId + ":" + version, new Stream(groupId, artifactId, version, null, sha1CheckSums));
    }

    ChannelManifest getRecordedChannel() {
        return new ChannelManifest(null, null, null, new ArrayList<Stream>(streams.values()));
    }
}
