/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

import java.util.Objects;

public class RuntimeChannel {

    private final Channel channel;
    private final ChannelManifest channelManifest;
    private final Blocklist blocklist;

    public RuntimeChannel(Channel channel, ChannelManifest channelManifest, Blocklist blocklist) {
        this.channel = channel;
        this.channelManifest = channelManifest;
        this.blocklist = blocklist;
    }

    public Channel getChannelDefinition() {
        return channel;
    }

    public ChannelManifest getChannelManifest() {
        return channelManifest;
    }

    public Blocklist getChannelBlocklist() {
        return blocklist;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuntimeChannel that = (RuntimeChannel) o;
        return Objects.equals(channel, that.channel) && Objects.equals(channelManifest, that.channelManifest) && Objects.equals(blocklist, that.blocklist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, channelManifest, blocklist);
    }
}
