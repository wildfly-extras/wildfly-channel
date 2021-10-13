/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChannelRecorder {

    private final List<Channel> recordedChannels = new ArrayList<>();

    public void recordStream(String groupId, String artifactId, String version, Channel channel) {
        Channel found = findOrCreateChannel(channel);
        found.addStream(new Stream(groupId, artifactId, version, null));
    }

    private Channel findOrCreateChannel(Channel channel) {
        Optional<Channel> found = recordedChannels.stream().filter(c -> c.isResolveWithLocalCache() == channel.isResolveWithLocalCache()
                && c.getRepositories().equals(channel.getRepositories()))
                .findFirst();
        if (found.isPresent()) {
            return found.get();
        }

        Channel newChannel = new Channel();
        newChannel.setResolveWithLocalCache(channel.isResolveWithLocalCache());
        newChannel.setRepositories(channel.getRepositories());
        recordedChannels.add(newChannel);
        return newChannel;
    }

    public List<Channel> getRecordedChannels() {
        return recordedChannels;
    }
}
