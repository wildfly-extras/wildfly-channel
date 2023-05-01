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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ChannelMapperTestCase {

    @Test
    public void testWriteReadChannel() throws Exception {
        final Channel channel = new Channel("test_name", "test_desc",
                new Vendor("test_vendor_name", Vendor.Support.COMMUNITY),
                List.of(new Repository("test", "https://test.org/repository")),
                new ChannelManifestCoordinate("test.channels", "channel"),
                new BlocklistCoordinate("test.block", "blocklist"),
                Channel.NoStreamStrategy.NONE, null);
        final String yaml = ChannelMapper.toYaml(channel);

        final Channel channel1 = ChannelMapper.fromString(yaml).get(0);
        assertEquals(Vendor.Support.COMMUNITY, channel1.getVendor().getSupport());
        assertFalse(yaml.contains("manifestCoordinate:"));
    }

    @Test
    public void testWriteMultipleChannels() throws Exception {
        final Channel channel1 = new Channel("test_name_1", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY),
                List.of(new Repository("test", "https://test.org/repository")),
                new ChannelManifestCoordinate("test.channels", "channel"),
                new BlocklistCoordinate("test.block", "blocklist"),
                Channel.NoStreamStrategy.NONE, null);
        final Channel channel2 = new Channel("test_name_2", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY),
                List.of(new Repository("test", "https://test.org/repository")),
                new ChannelManifestCoordinate(new URL("http://test.channels/channels")),
                new BlocklistCoordinate("test.block", "blocklist"),
                Channel.NoStreamStrategy.NONE, null);
        final String yaml = ChannelMapper.toYaml(channel1, channel2);

        System.out.println(yaml);
        List<Channel> channels = ChannelMapper.fromString(yaml);
        assertEquals(2, channels.size());
        final Channel c1 = channels.get(0);
        assertEquals(channel1.getName(), c1.getName());
        final Channel c2 = channels.get(1);
        assertEquals(channel2.getName(), c2.getName());
    }

    @Test
    public void testReadChannelWithUnknownProperties() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/channel-with-unknown-properties.yaml");

        Channel channel = ChannelMapper.from(file);
        assertNotNull(channel);
    }

    @Test
    public void writeChannelWithUrlBlocklist() throws Exception {
        Channel channel = new Channel("test_name_1", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY),
                List.of(new Repository("test", "https://test.org/repository")),
                new ChannelManifestCoordinate("test.channels", "channel"),
                new BlocklistCoordinate(new URL("http://test.te")),
                Channel.NoStreamStrategy.NONE, null);

        final String yaml = ChannelMapper.toYaml(channel);

        System.out.println(yaml);

        Channel readChannel = ChannelMapper.fromString(yaml).get(0);
        assertEquals(new URL("http://test.te"), readChannel.getBlocklistCoordinate().getUrl());
    }

    @Test
    public void writeChannelWithNoResolveStrategy() throws Exception {
        Channel channel = new Channel("test_name_1", "test_desc", new Vendor("test_vendor_name", Vendor.Support.COMMUNITY),
                List.of(new Repository("test", "https://test.org/repository")),
                new ChannelManifestCoordinate("test.channels", "channel"),
                new BlocklistCoordinate(new URL("http://test.te")),
                Channel.NoStreamStrategy.NONE, null);

        final String yaml = ChannelMapper.toYaml(channel);

        System.out.println(yaml);

        Channel readChannel = ChannelMapper.fromString(yaml).get(0);
        assertEquals(Channel.NoStreamStrategy.NONE, readChannel.getNoStreamStrategy());
    }
}
