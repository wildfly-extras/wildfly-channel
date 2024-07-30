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
package org.wildfly.channel.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.BlocklistCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Vendor;

public class ChannelTestCase {

    @Test
    public void nonExistingChannelTest() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("this-channel-does-not-exist.yaml");
        Assertions.assertThrows(RuntimeException.class, () -> {
            ChannelMapper.from(file);
        });
    }

    @Test()
    public void emptyChannelTest() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/empty-channel.yaml");
        Assertions.assertThrows(RuntimeException.class, () -> {
            ChannelMapper.from(file);
        });
    }

    @Test()
    public void multipleChannelsTest() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/multiple-channels.yaml");

        try (InputStream in = file.openStream())
        {
            byte[] bytes = in.readAllBytes();
            String content = new String(bytes, Charset.defaultCharset());
            List<Channel> channels = ChannelMapper.fromString(content);
            assertEquals(2, channels.size());
            assertEquals("Channel for WildFly 27", channels.get(0).getName());
            assertEquals("Channel for WildFly 28", channels.get(1).getName());
        }
    }

    @Test
    public void simpleChannelTest() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/simple-channel.yaml");

        Channel channel = ChannelMapper.from(file);

        assertEquals("My Channel", channel.getName());
        assertEquals("This is my channel\n" +
                "with my stuff", channel.getDescription());

        Vendor vendor = channel.getVendor();
        assertNotNull(vendor);
        assertEquals("My Vendor", vendor.getName());
        assertEquals(Vendor.Support.COMMUNITY, vendor.getSupport());
    }

    @Test
    public void channelWithBlocklist() throws MalformedURLException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/channel-with-blocklist.yaml");

        Channel channel = ChannelMapper.from(file);

        BlocklistCoordinate blocklist = channel.getBlocklistCoordinate();

        assertEquals("blocklist", blocklist.getArtifactId());
        assertEquals("org.wildfly", blocklist.getGroupId());
        assertEquals("1.2.3",  blocklist.getVersion());
    }

    @Test
    public void channelWithGpgCheck() throws IOException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL file = tccl.getResource("channels/channel-with-gpg-check.yaml");

        Channel channel = ChannelMapper.from(file);

        assertTrue(channel.isGpgCheck());
    }
}
